#!/usr/bin/env bash
# Drives the app with remote-control key events over ADB, to smoke-test D-pad navigation on the
# projector (or an Android TV emulator) without touching it. Watch the screen while it runs.
#
#   adb connect <projector-ip>:5555
#   scripts/remote-test.sh                 # uses the only connected device
#   ADB_SERIAL=192.168.1.50:5555 scripts/remote-test.sh
#
# Precondition: at least one PDF in the library (open one once via "Open file").
# This is a guided walkthrough, not an assertion test: where a D-pad press lands depends on the
# layout, so watch the screen and check focus, toolbar auto-hide and the BACK order.
set -euo pipefail

PKG=com.ebookvibe.tvreader
ADB=(adb ${ADB_SERIAL:+-s "$ADB_SERIAL"})
PAUSE=${PAUSE:-1.2}

key() {   # key <KEYCODE> [label]
  echo "  → ${2:-$1}"
  "${ADB[@]}" shell input keyevent "$1"
  sleep "$PAUSE"
}
hold() {  # long-press
  echo "  → long-press ${2:-$1}"
  "${ADB[@]}" shell input keyevent --longpress "$1"
  sleep "$PAUSE"
}
step() { echo; echo "== $*"; }

step "Launch from the Leanback launcher entry"
"${ADB[@]}" shell am force-stop "$PKG"
"${ADB[@]}" shell monkey -p "$PKG" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null
sleep 3

step "Library: focus starts on the most recent book; open it"
key KEYCODE_DPAD_CENTER "OK (open book)"
sleep 2

step "Page turning (toolbar should appear on each key and hide 4 s later)"
key KEYCODE_DPAD_RIGHT "next page"
key KEYCODE_DPAD_RIGHT "next page"
key KEYCODE_DPAD_LEFT  "previous page"
key KEYCODE_PAGE_DOWN  "Page Down"
key KEYCODE_CHANNEL_UP "Channel + (previous)"
echo "  (waiting for toolbar auto-hide)"; sleep 5

step "Go to page via digit key → keypad → Go"
key KEYCODE_5 "digit 5 opens Go to page"
key KEYCODE_DPAD_CENTER "Go"

step "Toolbar via OK, walk the controls"
key KEYCODE_DPAD_CENTER "OK opens toolbar (focus on Next)"
key KEYCODE_DPAD_DOWN  "down to view row"
key KEYCODE_DPAD_RIGHT "→ Zoom"
key KEYCODE_DPAD_CENTER "open Zoom dialog"
key KEYCODE_DPAD_DOWN  "Fit width"
key KEYCODE_DPAD_CENTER "select Fit width"

step "Zoomed: up/down pan, edge turns page"
key KEYCODE_DPAD_CENTER "OK toolbar"
key KEYCODE_BACK "BACK hides toolbar"
for _ in 1 2 3 4; do key KEYCODE_DPAD_DOWN "pan down"; done
key KEYCODE_DPAD_UP "pan up"

step "Pan mode toggle with long-press OK"
hold KEYCODE_DPAD_CENTER "OK"
key KEYCODE_DPAD_RIGHT "pan right (Pan mode)"
hold KEYCODE_DPAD_CENTER "OK (back to Turn mode)"

step "Theme cycle: MENU opens toolbar"
key KEYCODE_MENU "MENU"
key KEYCODE_DPAD_DOWN "view row"
for _ in 1 2 3 4; do key KEYCODE_DPAD_RIGHT "→"; done
key KEYCODE_DPAD_CENTER "Page theme → Sepia"
key KEYCODE_DPAD_CENTER "Page theme → Light"
key KEYCODE_DPAD_CENTER "Page theme → Dark"

step "Sleep timer dialog"
key KEYCODE_DPAD_UP "top row"
key KEYCODE_DPAD_RIGHT "→ Sleep"
key KEYCODE_DPAD_CENTER "open Sleep timer"
key KEYCODE_DPAD_DOWN "5 minutes"
key KEYCODE_DPAD_CENTER "start (badge appears top-right)"

step "BACK chain: toolbar → reader → app"
key KEYCODE_MENU "MENU (toolbar)"
key KEYCODE_BACK "BACK hides toolbar"
key KEYCODE_BACK "BACK leaves reader (page saved)"
key KEYCODE_BACK "BACK exits app"

echo
echo "Done. Re-open the app: the book card should show the page you were on."
