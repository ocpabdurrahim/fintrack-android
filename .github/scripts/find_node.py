"""Print the centre "x y" of the first node in a uiautomator dump that matches.

Usage: find_node.py ui.xml LABEL [--contains]

Compose merges a button's texts into one node ("🍔, Food"), so an exact match
also accepts LABEL as one of the comma-separated parts.
"""
import re
import sys
import xml.etree.ElementTree as ET


def matches(value, label, contains):
    if not value:
        return False
    if contains:
        return label in value
    return value == label or label in [part.strip() for part in value.split(",")]


def main():
    path, label = sys.argv[1], sys.argv[2]
    contains = len(sys.argv) > 3 and sys.argv[3] == "--contains"
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError):
        return
    for node in root.iter("node"):
        if matches(node.get("text"), label, contains) or matches(node.get("content-desc"), label, contains):
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                print((x1 + x2) // 2, (y1 + y2) // 2)
                return


main()
