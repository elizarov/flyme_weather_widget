#!/usr/bin/env python3
"""Generate ignored widget/overlay resources from local OEM APKs."""
import argparse
import copy
import json
import os
import subprocess
from pathlib import Path
import xml.etree.ElementTree as ET
from oem_resources import ANDROID, PRIVATE, ApkResources, write_xml

ROOT = Path(__file__).resolve().parents[1]


def try_xml(resources, paths, check=lambda root: True, **kwargs):
    problems = []
    for path in paths:
        try:
            root = resources.xml(path, **kwargs)
            if check(root):
                return root
            problems.append(path + ": required widget elements not found")
        except (ValueError, KeyError, subprocess.CalledProcessError) as error:
            problems.append(path + ": " + str(error))
    raise ValueError("Cannot extract required XML resource; tried:\n" + "\n".join(problems))


def prepare(launcher, weather, aapt2):
    generated = ROOT / "local-assets/generated"
    # Reject stale manifests after any failed regeneration.
    manifest = generated / "manifest.json"
    manifest.unlink(missing_ok=True)
    launcher_resources = ApkResources(aapt2, launcher)
    ns = "{" + PRIVATE + "}"
    def source_widgets(root):
        sections = [s for s in root if s.get(ns + "displayType") == "psd" and s.get(ns + "widgetDisplay") == "weather"]
        matches = [w for s in sections for w in s.iter() if w.get(ns + "widgetPackageName") == "com.flyme.auto.weather"]
        if not matches:
            matches = [w for w in root.iter() if w.get(ns + "widgetPackageName") == "com.flyme.auto.weather"]
        return matches
    profiles = try_xml(launcher_resources, launcher_resources.xml_paths("xml", ["widget_profiles"], r"widget.*profile"),
                       check=lambda root: bool(source_widgets(root)))
    replacements = 0
    for widget in source_widgets(profiles):
        widget.set(ns + "widgetPackageName", "me.elizarov.weather")
        widget.set(ns + "widgetClassName", "me.elizarov.weather.WeatherWidgetProvider")
        replacements += 1

    resources = ApkResources(aapt2, weather)
    attr = "{" + ANDROID + "}"
    required = {"appwidget_data_month", "appwidgetr_data_day", "appwidgetr_time", "appwidget_week", "appwidget_lunar_data", "appwidget_data_month_divide"}
    layout = try_xml(resources, resources.xml_paths("layout", ["weather_app_widget_l946_new", "weather_app_widget_light_l946_new",
                      "weather_app_widget", "weather_app_widget_light"], r"weather.*widget|widget.*weather"),
                     check=lambda root: required <= {n.get(attr + "id", "").rsplit("/", 1)[-1] for n in root.iter()}, prune_weather=True)
    layout[1].set(attr + "layout_height", "112px")
    divider_name = "appwidget_data_divider"
    for node in layout.iter():
        if node.get(attr + "id", "").endswith("/appwidget_data_month_divide"):
            divider_name = node.get(attr + "background", "@drawable/" + divider_name).rsplit("/", 1)[-1]
            node.set(attr + "background", "@drawable/appwidget_data_divider_light")
    ui = ET.parse(ROOT / "widget/ui/weather.xml").getroot()
    layout[1].append(ui[0])
    layout.append(ui[1])
    write_xml(layout, generated / "widget-res/layout/weather.xml")
    divider = try_xml(resources, resources.xml_paths("drawable", [divider_name, "appwidget_data_divider", "appwidget_data_divider_light"], r"appwidget.*divider"))
    for name, color in [("appwidget_data_divider", "#000000"), ("appwidget_data_divider_light", "#ffffff")]:
        variant = copy.deepcopy(divider)
        for node in variant.iter():
            for field in ["fillColor", "strokeColor", "tint"]:
                if attr + field in node.attrib:
                    node.set(attr + field, color)
        write_xml(variant, generated / f"widget-res/drawable/{name}.xml")
    fonts = ET.Element("resources")
    for name in ["font_display", "font_display_permission"]:
        entry = next((r for r in resources.resources.values() if r["name"] == "string/" + name), None)
        default = "LynkcoDisplay" if name == "font_display" else "Flyme Sans VF"
        ET.SubElement(fonts, "string", {"name": name, "translatable": "false"}).text = resources.value(entry["value"], "text") if entry else default
    write_xml(fonts, generated / "widget-res/values/fonts.xml")
    write_xml(profiles, generated / "overlay-res/xml/widget_profiles.xml")
    generated.mkdir(parents=True, exist_ok=True)
    manifest.write_text(json.dumps({"launcher": launcher.name, "weather": weather.name,
        "provider_replacements": replacements}, indent=2) + "\n")
    print(f"Generated local OEM resources in {generated}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--launcher", type=Path, default=ROOT / "local-assets/AutoLauncher.apk")
    parser.add_argument("--weather", type=Path, default=ROOT / "local-assets/AutoWeather.apk")
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or Path.home() / "Library/Android/sdk")
    parser.add_argument("--aapt2", type=Path, default=sdk / "build-tools/36.0.0/aapt2")
    args = parser.parse_args()
    try:
        prepare(args.launcher, args.weather, args.aapt2)
    except (OSError, ValueError, KeyError, StopIteration, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Cannot prepare OEM resources: {error}\nRun tools/pull-assets.py first.\n")
