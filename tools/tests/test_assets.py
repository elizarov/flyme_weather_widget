"""Synthetic fixtures only: no resources or font bytes from a vehicle."""
import copy
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from oem_resources import ANDROID, PRIVATE, ApkResources


def load(name):
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), TOOLS / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


pull = load("pull-assets")
prepare = load("prepare-assets")


class AssetsTest(unittest.TestCase):
    def test_apk_path_uses_current_user_and_handles_base_split(self):
        with patch.object(pull.subprocess, "check_output", return_value="package:/data/app/example/base.apk\npackage:/data/app/example/split.apk\n") as shell, patch.object(pull, "download") as download:
            pull.pull_apk(["adb", "-s", "example"], "example.package", Path("output.apk"))
            self.assertIn("current", shell.call_args.args[0])
            self.assertEqual(download.call_args.args[1], "/data/app/example/base.apk")
        with patch.object(pull.subprocess, "check_output", return_value=""), patch.object(pull, "download", side_effect=ValueError("missing")), self.assertRaises(ValueError):
            pull.pull_apk(["adb"], "missing.package", Path("output.apk"))

    def test_failed_download_retains_previous_file(self):
        with tempfile.TemporaryDirectory() as folder:
            destination = Path(folder) / "asset"
            destination.write_bytes(b"previous")
            def fail(command, **kwargs):
                Path(command[-1]).write_bytes(b"incomplete")
                raise subprocess.CalledProcessError(1, command)
            with patch.object(pull.subprocess, "run", side_effect=fail), self.assertRaises(subprocess.CalledProcessError):
                pull.download(["adb"], "/system/example", destination)
            self.assertEqual(destination.read_bytes(), b"previous")

    def test_fonts_choose_regular_face_and_ignore_unrelated_families(self):
        config = b'<familyset><family name="LynkcoDisplay"><font weight="700">bold.otf</font><font weight="400">regular.otf</font></family><family name="Other"><font>other.ttf</font></family></familyset>'
        downloads = []
        def download(adb, remote, target):
            downloads.append(remote)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(config if remote.endswith("fonts.xml") else remote.encode())
            return {"source": remote}
        with tempfile.TemporaryDirectory() as folder, patch.object(pull, "download", side_effect=download):
            local = Path(folder)
            pull.pull_fonts(["adb"], local)
            self.assertEqual((local / "res/font/lynkco_display.otf").read_bytes(), b"/system/fonts/regular.otf")
            self.assertNotIn("/system/fonts/other.ttf", downloads)
            self.assertTrue((local / "fonts/manifest.json").is_file())

    def test_xml_decoder_keeps_extra_attributes_and_decodes_enum(self):
        table = '    resource 0x7f010001 attr/displayType\n      () (attr) type=integer|enum size=1\n        psd(0x7f020001)=0x00000001\n'
        tree = 'N: example=http://schemas.android.com/apk/res-auto (line=1)\n  E: widgets (line=1)\n      E: section (line=2)\n        A: http://schemas.android.com/apk/res-auto:displayType(0x7f010001)=1\n        A: http://schemas.android.com/apk/res-auto:description(0x7f010002)="Synthetic & extra" (Raw: "Synthetic & extra")\n'
        def dump(instance, *args):
            return table if args[0] == "resources" else tree
        with patch.object(ApkResources, "dump", dump):
            root = ApkResources("aapt2", "synthetic.apk").xml("example.xml")
        self.assertEqual(root[0].get("{" + PRIVATE + "}displayType"), "psd")
        self.assertEqual(root[0].get("{" + PRIVATE + "}description"), "Synthetic & extra")

    def test_patch_preserves_unrelated_widgets_and_handles_multiple_weather_entries(self):
        profiles = ET.fromstring(f'<widgets xmlns:l="{PRIVATE}"><section l:displayType="psd" l:widgetDisplay="weather"><appwidget l:widgetPackageName="com.flyme.auto.weather" l:widgetClassName="OriginalProvider" l:extra="keep"/></section><section l:displayType="csd" l:widgetDisplay="other"><appwidget l:widgetPackageName="example.other"/></section></widgets>')
        layout = ET.fromstring(f'<LinearLayout xmlns:a="{ANDROID}"><RelativeLayout/><RelativeLayout/></LinearLayout>')
        for name in ["appwidget_data_month", "appwidgetr_data_day", "appwidgetr_time", "appwidget_week", "appwidget_lunar_data", "appwidget_data_month_divide"]:
            ET.SubElement(layout[0], "TextView", {"{" + ANDROID + "}id": "@+id/" + name})
        class FakeApk:
            resources = {str(i): {"name": "string/" + name, "value": '"Example"'} for i, name in enumerate(["font_display", "font_display_permission"])}
            def __init__(self, *args): pass
            def value(self, value, attribute): return value.strip('"')
            def xml_paths(self, kind, preferred, pattern): return [f"res/{kind}/{preferred[0]}.xml"]
            def xml(self, path, **kwargs):
                return copy.deepcopy(profiles if "widget_profiles" in path else layout if "/layout/" in path else ET.Element("vector"))
        with tempfile.TemporaryDirectory() as folder, patch.object(prepare, "ROOT", Path(folder)), patch.object(prepare, "ApkResources", FakeApk):
            root = Path(folder)
            (root / "widget/ui").mkdir(parents=True)
            (root / "widget/ui/weather.xml").write_text("<weather-ui><LinearLayout/><LinearLayout/><LinearLayout/></weather-ui>")
            apk = root / "synthetic.apk"
            apk.write_bytes(b"synthetic")
            prepare.prepare(apk, apk, Path("aapt2"))
            output = ET.parse(root / "local-assets/generated/overlay-res/xml/widget_profiles.xml").getroot()
            ns = "{" + PRIVATE + "}"
            self.assertEqual(output[0][0].get(ns + "widgetPackageName"), "me.elizarov.weather")
            self.assertEqual(output[0][0].get(ns + "widgetClassName"), "me.elizarov.weather.WeatherWidgetProvider")
            self.assertEqual(output[0][0].get(ns + "extra"), "keep")
            self.assertEqual(output[1][0].attrib, profiles[1][0].attrib)
            profiles[0].append(copy.deepcopy(profiles[0][0]))
            prepare.prepare(apk, apk, Path("aapt2"))
            output = ET.parse(root / "local-assets/generated/overlay-res/xml/widget_profiles.xml").getroot()
            self.assertTrue(all(w.get(ns + "widgetPackageName") == "me.elizarov.weather" for w in output[0]))
            profiles.remove(profiles[0])
            with self.assertRaises(ValueError):
                prepare.prepare(apk, apk, Path("aapt2"))
            self.assertFalse((root / "local-assets/generated/manifest.json").exists())

    def test_apk_falls_back_to_expected_system_path(self):
        def download(adb, remote, target):
            if remote == "/system/app/AutoWeather/AutoWeather.apk": return {"source": remote}
            raise ValueError("missing")
        with patch.object(pull.subprocess, "check_output", side_effect=subprocess.CalledProcessError(1, "pm")), patch.object(pull, "download", side_effect=download):
            self.assertEqual(pull.pull_apk(["adb"], "com.flyme.auto.weather", Path("AutoWeather.apk"))["source"], "/system/app/AutoWeather/AutoWeather.apk")

    def test_fonts_can_be_retrieved_without_font_configuration(self):
        def download(adb, remote, target):
            if remote != "/product/fonts/LynkcoDisplay-Regular.otf": raise ValueError("missing")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(b"synthetic-font")
            return {"source": remote}
        with tempfile.TemporaryDirectory() as folder, patch.object(pull, "download", side_effect=download):
            pull.pull_fonts(["adb"], Path(folder))
            self.assertEqual((Path(folder) / "res/font/lynkco_display.otf").read_bytes(), b"synthetic-font")

    def test_missing_additional_font_does_not_block_regular_face(self):
        config = b'<familyset><family name="LynkcoDisplay"><font weight="700">missing.otf</font><font weight="400">regular.otf</font></family></familyset>'
        def download(adb, remote, target):
            if remote not in {"/system/etc/fonts.xml", "/system/fonts/regular.otf"}: raise ValueError("missing")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(config if remote.endswith("fonts.xml") else b"regular")
            return {"source": remote}
        with tempfile.TemporaryDirectory() as folder, patch.object(pull, "download", side_effect=download):
            pull.pull_fonts(["adb"], Path(folder))
            self.assertEqual((Path(folder) / "res/font/lynkco_display.otf").read_bytes(), b"regular")

    def test_xml_alternative_path_is_tried_after_missing_primary(self):
        class Source:
            def xml(self, path, **kwargs):
                if path == "missing": raise ValueError("missing")
                return ET.Element("found")
        self.assertEqual(prepare.try_xml(Source(), ["missing", "alternative"]).tag, "found")

    def test_resource_table_file_path_wins_over_version_specific_filename(self):
        table = '    resource 0x7f010001 layout/weather_app_widget\n      () (file) res/layout/new_weather_widget.xml type=XML\n'
        with patch.object(ApkResources, "dump", return_value=table):
            source = ApkResources("aapt2", "synthetic.apk")
        self.assertEqual(source.xml_paths("layout", ["weather_app_widget"], "weather")[0], "res/layout/new_weather_widget.xml")


if __name__ == "__main__":
    unittest.main()
