#!/usr/bin/env python3
"""Create a persistent local release key and sign the resource overlay with it."""
import argparse
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SIGNING = ROOT / "signing"
CONFIG = SIGNING / "release.properties"


def read_config():
    if not CONFIG.is_file():
        raise ValueError("Release signing is not configured. Run python3 tools/signing.py init")
    values = {}
    for line in CONFIG.read_text().splitlines():
        if line.strip() and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    for name in ["storeFile", "storeType", "storePassword", "keyAlias", "keyPassword"]:
        if not values.get(name):
            raise ValueError(f"Missing {name} in signing/release.properties")
    values["storeFile"] = str((SIGNING / values["storeFile"]).resolve())
    if not Path(values["storeFile"]).is_file():
        raise ValueError("Release keystore is missing. Restore signing/ from your backup; do not replace the key for an existing release.")
    return values


def password_env(values):
    return {**os.environ, "WEATHER_STORE_PASSWORD": values["storePassword"],
            "WEATHER_KEY_PASSWORD": values["keyPassword"]}


def initialize():
    key = SIGNING / "release.keystore"
    if CONFIG.exists():
        read_config()
        print("Using existing release signing key in signing/.")
        return
    if key.exists():
        raise ValueError("A keystore already exists without release.properties. Restore its signing configuration instead of generating a new key.")
    java_home = os.environ.get("JAVA_HOME")
    keytool = str(Path(java_home) / "bin/keytool") if java_home else shutil.which("keytool")
    studio_keytool = Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool")
    if not java_home and studio_keytool.is_file():
        keytool = str(studio_keytool)
    if not keytool:
        raise ValueError("keytool not found. Configure JAVA_HOME for a JDK.")
    SIGNING.mkdir(parents=True, exist_ok=True, mode=0o700)
    password = secrets.token_hex(32)
    values = {"storeFile": "release.keystore", "storeType": "PKCS12", "storePassword": password,
              "keyAlias": "weather-widget", "keyPassword": password}
    with tempfile.TemporaryDirectory(dir=SIGNING) as temp:
        generated = Path(temp) / "release.keystore"
        subprocess.run([keytool, "-genkeypair", "-keystore", str(generated), "-storetype", "PKCS12",
                        "-alias", values["keyAlias"], "-keyalg", "RSA", "-keysize", "3072",
                        "-validity", "36500", "-dname", "CN=WeatherWidget",
                        "-storepass:env", "WEATHER_STORE_PASSWORD", "-keypass:env", "WEATHER_KEY_PASSWORD"],
                       env=password_env(values), check=True)
        properties = Path(temp) / "release.properties"
        properties.write_text("# Local release signing; back up the entire signing directory.\n" +
                              "".join(f"{name}={value}\n" for name, value in values.items()))
        generated.chmod(0o600)
        properties.chmod(0o600)
        generated.replace(key)
        properties.replace(CONFIG)
    print("Created release signing key in signing/. Back up this directory; it is excluded from Git.")


def sign(apksigner, source, output):
    values = read_config()
    subprocess.run([str(apksigner), "sign", "--ks", values["storeFile"], "--ks-type", values["storeType"],
                    "--ks-key-alias", values["keyAlias"], "--ks-pass", "env:WEATHER_STORE_PASSWORD",
                    "--key-pass", "env:WEATHER_KEY_PASSWORD", "--out", str(output), str(source)],
                   env=password_env(values), check=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("init")
    signer = commands.add_parser("sign")
    signer.add_argument("--apksigner", type=Path, required=True)
    signer.add_argument("--input", type=Path, required=True)
    signer.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == "init":
            initialize()
        else:
            sign(args.apksigner, args.input, args.output)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Release signing failed: {error}\n")
