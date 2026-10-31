#!/usr/bin/env python3
"""Continue from saved host: dismiss dialogs, list sessions, enter, persist check."""
import re
import subprocess
import time
from pathlib import Path

OUT = Path.home() / "mtmux-e2e" / "run4" / "screenshots"
OUT.mkdir(parents=True, exist_ok=True)
n = 20


def adb(*a):
    return subprocess.run(["adb", *a], capture_output=True, text=True, timeout=90)


def shot(name):
    global n
    n += 1
    adb("shell", "screencap", "-p", "/sdcard/s.png")
    dest = OUT / f"{n:03d}_{name}.png"
    adb("pull", "/sdcard/s.png", str(dest))
    print("SHOT", dest.name, flush=True)


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    adb("pull", "/sdcard/ui.xml", "/tmp/u.xml")
    return Path("/tmp/u.xml").read_text(errors="ignore")


def tap_text(text, retries=6):
    for _ in range(retries):
        xml = dump()
        for pat in (
            rf'text="{re.escape(text)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            rf'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="{re.escape(text)}"',
        ):
            m = re.search(pat, xml)
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(0.7)
                return True
        time.sleep(0.4)
    return False


def tap_id(rid, retries=6):
    for _ in range(retries):
        xml = dump()
        for pat in (
            rf'resource-id="[^"]*{re.escape(rid)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            rf'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*resource-id="[^"]*{re.escape(rid)}"',
            rf'content-desc="{re.escape(rid)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
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
print("start_texts", [t for t in re.findall(r'text="([^"]+)"', xml) if len(t) < 50][:50], flush=True)

for t in ["接受", "信任", "确认", "确定", "继续", "OK", "关闭"]:
    if tap_text(t, retries=1):
        print("tapped", t, flush=True)
        shot(f"dlg_{t}")
        time.sleep(1)

for _ in range(2):
    xml = dump()
    if "会话" in xml and "主机" in xml:
        break
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.6)

shot("after_dismiss")
ok = tap_text("会话") or tap_id("tab_sessions")
print("tap_sessions", ok, flush=True)
time.sleep(1)
shot("sessions_tab")

tap_id("session_refresh") or tap_text("Refresh") or tap_text("刷新")
adb("shell", "input", "swipe", "540", "500", "540", "1500", "400")
time.sleep(10)
for t in ["接受", "信任", "确认", "确定", "继续", "OK"]:
    if tap_text(t, retries=1):
        print("hk", t, flush=True)
        shot(f"hk_{t}")
        time.sleep(2)
time.sleep(5)
shot("sessions")
xml = dump()
print("sess", [t for t in re.findall(r'text="([^"]+)"', xml) if len(t) < 40][:50], flush=True)

entered = False
for name in ["cliproxy", "tvapk", "e2e"]:
    if tap_id(f"session_enter_{name}"):
        entered = True
        print("enter via tag", name, flush=True)
        break
if not entered:
    for name in ["cliproxy", "tvapk", "e2e"]:
        if name in xml and tap_text("进入", retries=2):
            entered = True
            print("enter text near", name, flush=True)
            break
if not entered:
    entered = bool(tap_text("进入", retries=2))
print("ENTERED", entered, flush=True)
time.sleep(10)
for t in ["接受", "确认", "确定"]:
    tap_text(t, retries=1)
time.sleep(5)
shot("terminal")
xml = dump()
print("term_texts", [t for t in re.findall(r'text="([^"]+)"', xml) if len(t) < 40][:40], flush=True)

adb("shell", "input", "tap", "540", "900")
time.sleep(0.3)
adb("shell", "input", "text", "echo e2e-pass4-ok")
adb("shell", "input", "keyevent", "66")
time.sleep(2)
shot("terminal_echo")

adb("shell", "am", "force-stop", "app.mtmux.android")
adb("shell", "monkey", "-p", "app.mtmux.android", "-c", "android.intent.category.LAUNCHER", "1")
time.sleep(3)
tap_text("主机")
time.sleep(1)
shot("reopen_hosts")
xml = dump()
print("PERSIST", "tencentvps" in xml or "49.235" in xml, flush=True)
print("CONT_DONE", flush=True)
