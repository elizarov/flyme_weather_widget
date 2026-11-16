"""Decode selected local APK resources using aapt2; no OEM data is embedded here."""
import json
import re
import subprocess
import xml.etree.ElementTree as ET

ANDROID = "http://schemas.android.com/apk/res/android"
PRIVATE = "http://schemas.android.com/apk/prv/res/com.flyme.auto.launcher"
ET.register_namespace("android", ANDROID)
ET.register_namespace("launcher", PRIVATE)


def scalar(value):
    if value.startswith('"'):
        return json.JSONDecoder().raw_decode(value)[0]
    return value


class ApkResources:
    def __init__(self, aapt2, apk):
        self.aapt2, self.apk = str(aapt2), str(apk)
        self.resources = {}
        current = None
        for line in self.dump("resources").splitlines():
            entry = re.match(r"\s*resource (0x[0-9a-f]+) (\S+)", line)
            if entry:
                current = {"name": entry[2], "value": None, "symbols": {}}
                self.resources[entry[1]] = current
            elif current is not None:
                default = re.match(r"\s+\(\) (.*)", line)
                symbol = re.match(r"\s+(\w+)\(0x[0-9a-f]+\)=(0x[0-9a-f]+)", line)
                if default:
                    current["value"] = default[1]
                elif symbol:
                    current["symbols"][int(symbol[2], 16)] = symbol[1]

    def dump(self, *args):
        return subprocess.check_output([self.aapt2, "dump", *args, self.apk], text=True, stderr=subprocess.PIPE)

    def xml_paths(self, kind, preferred, pattern):
        paths = []
        names = [kind + "/" + name for name in preferred]
        names += [r["name"] for r in self.resources.values() if r["name"].startswith(kind + "/") and re.search(pattern, r["name"])]
        for name in dict.fromkeys(names):
            resource = next((r for r in self.resources.values() if r["name"] == name), None)
            match = re.match(r"\(file\) (\S+) type=XML", (resource or {}).get("value") or "")
            paths.append(match[1] if match else "res/" + name + ".xml")
        return list(dict.fromkeys(paths))

    def value(self, value, attribute, attribute_id=None):
        if attribute == "textAppearance":
            return "@android:style/TextAppearance.Material"
        if attribute == "textColor":
            return "#f2ffffff"
        if value.startswith("@0x"):
            resource = self.resources.get(value[1:])
            if resource is None:
                raise ValueError(f"Unresolved {attribute}: {value}")
            kind, name = resource["name"].split("/", 1)
            if kind == "id":
                return "@+id/" + name
            if kind == "drawable":
                return "@drawable/" + name
            if kind == "string" and name in {"font_display", "font_display_permission"}:
                return "@string/" + name
            if kind not in {"dimen", "color", "string"} or resource["value"] is None:
                raise ValueError(f"Unsupported resource: {resource['name']}")
            return self.value(resource["value"], attribute, attribute_id)
        if value.startswith('"'):
            return scalar(value)
        if attribute in {"layout_width", "layout_height"}:
            if value in {"-1", "-2"}:
                return {"-1": "match_parent", "-2": "wrap_content"}[value]
        if attribute == "orientation":
            return {"0": "horizontal", "1": "vertical"}[value]
        if attribute == "textStyle":
            return {0: "normal", 1: "bold", 2: "italic", 3: "bold|italic"}[int(value, 0)]
        if attribute == "ellipsize":
            return {"1": "start", "2": "middle", "3": "end", "4": "marquee"}[value]
        # Android gravity flags; numeric XML values are not accepted as enums.
        if attribute in {"gravity", "layout_gravity"}:
            flags = int(value, 0)
            horizontal = {0: None, 1: "center_horizontal", 3: "left", 5: "right", 7: "fill_horizontal"}[flags & 7]
            if flags & 0x800000:
                horizontal = {"left": "start", "right": "end"}.get(horizontal, horizontal)
            vertical = {0: None, 0x10: "center_vertical", 0x30: "top", 0x50: "bottom", 0x70: "fill_vertical"}[flags & 0x70]
            return "|".join(v for v in [horizontal, vertical] if v) or "no_gravity"
        symbols = self.resources.get(attribute_id, {}).get("symbols", {})
        if symbols:
            return symbols[int(value, 0)]
        return value

    def xml(self, path, prune_weather=False):
        root, stack = None, []
        # Parse SDK's decoded XML tree rather than carrying an APK decoder dependency.
        for line in self.dump("xmltree", "--file", path).splitlines():
            element = re.match(r"(\s*)E: (\S+) \(line=\d+\)", line)
            attribute = re.match(r"\s*A: (.*):([^:(]+)(?:\((0x[0-9a-f]+)\))?=(.*)", line)
            if element:
                depth = len(element[1])
                while stack and stack[-1][0] >= depth:
                    stack.pop()
                node = ET.Element(element[2])
                if stack:
                    stack[-1][1].append(node)
                else:
                    if root is not None:
                        raise ValueError("Multiple XML roots")
                    root = node
                stack.append((depth, node))
            elif attribute:
                namespace, name, attr_id, raw = attribute.groups()
                if namespace.endswith("/res-auto"):
                    namespace = PRIVATE
                stack[-1][1].set("{" + namespace + "}" + name, raw)
            elif line.strip() and not line.lstrip().startswith("N:"):
                raise ValueError(f"Unsupported XML tree entry: {line}")
        if root is None:
            raise ValueError(f"Missing XML root in {path}")
        if prune_weather:
            # Remove OEM weather UI before resolving its unrelated resources.
            for child in list(root)[2:]:
                root.remove(child)
            if len(root) != 2:
                raise ValueError("Expected date/time and weekday rows")
            for child in list(root[1]):
                raw_id = child.get("{" + ANDROID + "}id", "").removeprefix("@")
                name = self.resources.get(raw_id, {}).get("name", "")
                if name.rsplit("/", 1)[-1] in {"appwidget_temperature", "appwidget_temperature_symbol", "appwidget_weather_status"}:
                    root[1].remove(child)
        for node in root.iter():
            for key, raw in list(node.attrib.items()):
                name = key.rsplit("}", 1)[-1]
                # Private enum names come from this APK's attr definitions.
                attr_id = next((k for k, r in self.resources.items() if r["name"] == "attr/" + name), None) if key.startswith("{" + PRIVATE + "}") else None
                node.set(key, self.value(raw, name, attr_id))
            if "{" + ANDROID + "}textAppearance" in node.attrib:
                node.set("{" + ANDROID + "}ellipsize", "end")
        return root


def write_xml(root, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(root)
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)
