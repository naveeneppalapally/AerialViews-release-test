#!/usr/bin/env bash
# uiclick.sh — click a UI element by text using UIAutomator dump
# Usage: ./uiclick.sh "Element Text" [serial]
#        ./uiclick.sh --dump [serial]           — just dump and pretty-print
#        ./uiclick.sh --find "Text" [serial]    — print bounds without clicking

set -euo pipefail

ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
SERIAL="${2:-emulator-5554}"
ADB_CMD="$ADB -s $SERIAL"
DUMP_PATH=/sdcard/ui_dump.xml
LOCAL_DUMP=/tmp/ui_dump_$SERIAL.xml

dump_ui() {
    $ADB_CMD shell uiautomator dump "$DUMP_PATH" >/dev/null 2>&1
    $ADB_CMD pull "$DUMP_PATH" "$LOCAL_DUMP" >/dev/null 2>&1
}

bounds_to_center() {
    # Input: [left,top][right,bottom]
    echo "$1" | python3 -c "
import sys, re
m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', sys.stdin.read().strip())
if m:
    l,t,r,b = int(m.group(1)),int(m.group(2)),int(m.group(3)),int(m.group(4))
    print((l+r)//2, (t+b)//2)
"
}

find_element() {
    local text="$1"
    # Try exact text= match first, then content-desc=
    python3 -c "
import sys, re
xml = open('$LOCAL_DUMP').read()
patterns = [
    r'text=\"$text\"[^>]*bounds=\"([^\"]+)\"',
    r'bounds=\"([^\"]+)\"[^>]*text=\"$text\"',
    r'content-desc=\"$text\"[^>]*bounds=\"([^\"]+)\"',
    r'bounds=\"([^\"]+)\"[^>]*content-desc=\"$text\"',
]
for p in patterns:
    m = re.search(p, xml)
    if m:
        print(m.group(1))
        sys.exit(0)
sys.exit(1)
" 2>/dev/null
}

case "${1:-}" in
    --dump)
        dump_ui
        python3 -c "
import re
xml = open('$LOCAL_DUMP').read()
nodes = re.findall(r'text=\"([^\"]+)\"', xml)
for n in nodes:
    if n.strip():
        print(' ', n)
"
        ;;
    --find)
        shift
        text="$1"
        SERIAL="${2:-emulator-5554}"
        ADB_CMD="$ADB -s $SERIAL"
        dump_ui
        bounds=$(find_element "$text") || { echo "NOT FOUND: $text"; exit 1; }
        echo "Bounds: $bounds"
        read cx cy <<< $(bounds_to_center "$bounds")
        echo "Center: $cx,$cy"
        ;;
    *)
        text="$1"
        dump_ui
        bounds=$(find_element "$text") || {
            echo "Element not found: '$text'"
            echo "Visible text elements:"
            python3 -c "
import re
xml = open('$LOCAL_DUMP').read()
nodes = re.findall(r'text=\"([^\"]+)\"', xml)
for n in sorted(set(nodes)):
    if n.strip(): print('  ', n)
" 2>/dev/null
            exit 1
        }
        read cx cy <<< $(bounds_to_center "$bounds")
        echo "Clicking '$text' at $cx,$cy"
        $ADB_CMD shell input tap "$cx" "$cy"
        sleep 0.5
        ;;
esac
