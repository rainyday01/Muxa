#!/usr/bin/env python3
import re
import subprocess
import time
from pathlib import Path

OUT = Path.home() / "mtmux-e2e" / "run3"
SHOT = OUT / "screenshots"
LOG = OUT / "logs"
SHOT.mkdir(parents=True, exist_ok=True)
LOG.mkdir(parents=True, exist_ok=True)
n = 200


def adb(*a):
    return subprocess.run(["adb", *a], capture_output=True, text=True, timeout=60)


def shot(name):
    global n
    n += 1
    adb("shell", "screencap", "-p", "/sdcard/s.png")
    adb("pull", "/sdcard/s.png", str(SHOT / f"{n:03d}_{name}.png"))
    print("SHOT", n, name, flush=True)


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    adb("pull", "/sdcard/ui.xml", str(LOG / "ui.xml"))
    return (LOG / "ui.xml").read_text(errors="ignore")


def tap_text(t, r=6):
    for _ in range(r):
        xml = dump()
        for pat in (
            rf'text="{re.escape(t)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            rf'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="{re.escape(t)}"',
        ):
            m = re.search(pat, xml)
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(0.6)
                return True
        time.sleep(0.4)
    return False


def tap_id(rid, r=6):
    for _ in range(r):
        xml = dump()
        for pat in (
            rf'resource-id="[^"]*{re.escape(rid)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            rf'content-desc="{re.escape(rid)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            rf'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*resource-id="[^"]*{re.escape(rid)}"',
        ):
            m = re.search(pat, xml)
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(0.7)
                return True
        time.sleep(0.4)
    return False


shot("cont_start")
xml = dump()
print("texts", re.findall(r'text="([^"]{1,40})"', xml)[:50], flush=True)
if any(x in xml for x in ("host_save", "screen_host_editor", "添加主机", "编辑主机")):
    tap_id("host_editor_back") or adb("shell", "input", "keyevent", "4")
    time.sleep(1)
shot("after_back")
tap_text("会话")
time.sleep(1)
shot("sessions_tab")
tap_id("session_refresh")
adb("shell", "input", "swipe", "540", "500", "540", "1500", "400")
time.sleep(8)
for t in ["接受", "信任", "确认", "确定", "继续", "OK", "Allow"]:
    if tap_text(t, r=1):
        shot("hostkey_" + t)
        time.sleep(3)
time.sleep(5)
shot("sessions_list")
xml = dump()
(LOG / "sessions2.xml").write_text(xml)
print("session texts", [t for t in re.findall(r'text="([^"]+)"', xml) if len(t) < 50][:60], flush=True)
for name in ["cliproxy", "tvapk"]:
    if tap_id(f"session_enter_{name}"):
        print("enter tag", name, flush=True)
        break
else:
    print("enter text", tap_text("进入"), flush=True)
time.sleep(10)
for t in ["接受", "确认", "确定"]:
    tap_text(t, r=1)
    time.sleep(1)
time.sleep(5)
shot("terminal")
adb("shell", "input", "tap", "540", "1000")
time.sleep(0.3)
adb("shell", "input", "text", "echo e2e-tags-ok")
adb("shell", "input", "keyevent", "66")
time.sleep(2)
shot("terminal_echo")
print("CONT_DONE", flush=True)
