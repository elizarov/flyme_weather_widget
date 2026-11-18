#!/usr/bin/env python3
"""Check signed release artifacts and ensure development-only content is absent."""
import argparse
from pathlib import Path
import re
import subprocess
import zipfile


def certificate(apksigner, apk):
    output = subprocess.check_output([str(apksigner), "verify", "--print-certs", str(apk)],
                                     text=True, stderr=subprocess.PIPE)
    match = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]+)", output)
    if not match:
        raise ValueError(f"Cannot identify the signer of {apk.name}")
    return match[1]


def verify(aapt2, apksigner, widget, overlay, test):
    # Certificate comparison checks our signing configuration, not OEM firmware versions.
    signatures = {certificate(apksigner, apk) for apk in [widget, overlay, test]}
    if len(signatures) != 1:
        raise ValueError("Widget, overlay and release tests must use the same signing key")
    for apk in [widget, overlay]:
        manifest = subprocess.check_output([str(aapt2), "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)], text=True)
        if re.search(r":debuggable(?:\([^)]*\))?=true", manifest):
            raise ValueError(f"{apk.name} is debuggable")
    with zipfile.ZipFile(widget) as archive:
        names = archive.namelist()
        if any(name.endswith((".ttf", ".otf", "/spb.xml", "/spb-details.html")) for name in names):
            raise ValueError("Release contains local preview fonts or test fixtures")
        if any("preview_flyme_screen" in name for name in names):
            raise ValueError("Release contains the local car screenshot")
        dex = b"".join(archive.read(name) for name in names if re.fullmatch(r"classes\d*\.dex", name))
        for name in ["PreviewActivity", "IconGalleryActivity", "OverlayProbeReceiver", "FlymePreviewBackdrop"]:
            if ("Lme/elizarov/weather/" + name + ";").encode() in dex:
                raise ValueError(f"Release contains development class {name}")
    print("PASS: release APKs are signed with one key, not debuggable, and contain no development classes, preview fonts or test fixtures.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["aapt2", "apksigner", "widget", "overlay", "test"]:
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    try:
        verify(args.aapt2, args.apksigner, args.widget, args.overlay, args.test)
    except (OSError, ValueError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Release verification failed: {error}\n")
