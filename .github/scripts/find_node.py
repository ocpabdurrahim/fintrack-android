"""Look up a node in a uiautomator dump.

Usage:
  find_node.py ui.xml TAG --id        centre "x y" of the node with that test tag
  find_node.py ui.xml TAG --text-of   text of the node with that test tag
  find_node.py ui.xml TEXT            centre of the first node showing TEXT

The app exposes its Compose test tags as resource ids (testTagsAsResourceId),
which is what --id and --text-of match.
"""
import re
import sys
import xml.etree.ElementTree as ET


def centre(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return f"{(x1 + x2) // 2} {(y1 + y2) // 2}"


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    path, key = sys.argv[1], sys.argv[2]
    mode = sys.argv[3] if len(sys.argv) > 3 else "--text"
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError):
        return
    for node in root.iter("node"):
        rid = node.get("resource-id", "")
        text = node.get("text", "")
        if mode in ("--id", "--text-of"):
            if rid == key or rid.endswith(":id/" + key):
                print(text if mode == "--text-of" else centre(node))
                return
        elif text == key or node.get("content-desc") == key:
            print(centre(node))
            return


main()
