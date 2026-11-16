#!/usr/bin/env python3
"""Fetch local OEM APKs and optional preview fonts via read-only ADB commands."""
import argparse
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def download(adb, remote, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    # A failed transfer must not replace the last complete local file.
    with tempfile.TemporaryDirectory(dir=destination.parent) as temp:
        part = Path(temp) / "download"
        subprocess.run([*adb, "pull", remote, str(part)], check=True)
        if not part.is_file() or not part.stat().st_size:
            raise ValueError(f"Empty download: {remote}")
        part.replace(destination)
    return {"source": remote}


def pull_apk(adb, package, destination):
    attempts = []
    for options in [["--user", "current"], [], ["--user", "0"]]:
        try:
            output = subprocess.check_output([*adb, "shell", "pm", "path", *options, package], text=True, stderr=subprocess.PIPE)
            paths = [line.removeprefix("package:").strip() for line in output.splitlines() if line.startswith("package:")]
            paths.sort(key=lambda p: PurePosixPath(p).name != "base.apk")
            for remote in paths:
                if "/split" not in remote and remote not in attempts:
                    attempts.append(remote)
                    try:
                        return download(adb, remote, destination)
                    except (OSError, ValueError, subprocess.CalledProcessError):
                        pass
        except (OSError, subprocess.CalledProcessError):
            pass
    name = destination.stem
    for partition in ["system/priv-app", "system/app", "product/priv-app", "product/app", "system_ext/priv-app", "system_ext/app"]:
        remote = f"/{partition}/{name}/{name}.apk"
        if remote in attempts:
            continue
        attempts.append(remote)
        try:
            return download(adb, remote, destination)
        except (OSError, ValueError, subprocess.CalledProcessError):
            pass
    raise ValueError(f"Cannot retrieve APK for {package}; tried: {', '.join(attempts)}")


def pull_fonts(adb, local):
    result, selected = {}, []
    normalize = lambda value: ''.join(c.lower() for c in value if c.isalnum())
    for partition, filename in [("system", "fonts.xml"), ("product", "fonts_customization.xml"), ("system_ext", "fonts_customization.xml"), ("vendor", "fonts.xml")]:
        config = local / f"fonts/{partition}-{filename}"
        try:
            record = download(adb, f"/{partition}/etc/{filename}", config)
            families = ET.parse(config).getroot()
            result[str(config.relative_to(local))] = record
            for family in families.iter("family"):
                if normalize(family.get("name", "")) in {"lynkcodisplay", "flymesansvf"}:
                    selected.append(family)
        except (OSError, ValueError, ET.ParseError, subprocess.CalledProcessError):
            continue
    digit_font = None
    tried = set()
    for family in selected:
        for font in family.findall("font"):
            source = (font.text or font.get("filename", "")).strip()
            name = PurePosixPath(source).name
            if not name:
                continue
            target = local / "fonts" / name
            regular = normalize(family.get("name", "")) == "lynkcodisplay" and font.get("weight", "400") == "400" and font.get("style", "normal") == "normal"
            if str(target.relative_to(local)) in result:
                if regular:
                    digit_font = target
                continue
            for remote in ([source] if source.startswith("/") else []) + [f"/{p}/fonts/{name}" for p in ["system", "product", "system_ext", "vendor"]]:
                if remote in tried:
                    continue
                tried.add(remote)
                try:
                    result[str(target.relative_to(local))] = download(adb, remote, target)
                    if regular:
                        digit_font = target
                    break
                except (OSError, ValueError, subprocess.CalledProcessError):
                    continue
    if digit_font is None:
        for partition in ["system", "product", "system_ext", "vendor"]:
            remote = f"/{partition}/fonts/LynkcoDisplay-Regular.otf"
            target = local / "fonts/LynkcoDisplay-Regular.otf"
            try:
                result[str(target.relative_to(local))] = download(adb, remote, target)
                digit_font = target
                break
            except (OSError, ValueError, subprocess.CalledProcessError):
                continue
    if digit_font is None:
        raise ValueError("Cannot retrieve the regular display font from system/product/system_ext/vendor fonts")
    resource = local / "res/font/lynkco_display.otf"
    resource.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(digit_font, resource)
    result[str(resource.relative_to(local))] = result[str(digit_font.relative_to(local))]
    (local / "fonts/manifest.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", help="ADB device serial (otherwise ANDROID_SERIAL / ADB default)")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    default_adb = str(Path(sdk) / "platform-tools/adb") if sdk else shutil.which("adb") or str(Path.home() / "Library/Android/sdk/platform-tools/adb")
    parser.add_argument("--adb", default=default_adb)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--fonts-only", action="store_true")
    group.add_argument("--apks-only", action="store_true")
    args = parser.parse_args()
    adb = [args.adb]
    if args.serial:
        adb += ["-s", args.serial]
    local = ROOT / "local-assets"
    try:
        if not args.fonts_only:
            records = {}
            for package, name in [("com.flyme.auto.launcher", "AutoLauncher"), ("com.flyme.auto.weather", "AutoWeather")]:
                records[name + ".apk"] = pull_apk(adb, package, local / (name + ".apk"))
            (local / "apks-manifest.json").write_text(json.dumps(records, indent=2) + "\n")
        if not args.apks_only:
            pull_fonts(adb, local)
    except (OSError, ValueError, ET.ParseError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Cannot fetch local assets: {error}\n")
    print(f"Saved local assets in {local} (ignored by Git).")


if __name__ == "__main__":
    main()
