#!/usr/bin/env python3
"""Accept '信任并继续' host-key dialog, list sessions, enter terminal."""
import re
import subprocess
import time
from pathlib import Path

OUT = Path.home() / "mtmux-e2e" / "run4" / "screenshots"
OUT.mkdir(parents=True, exist_ok=True)
n = 40


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


def texts(xml=None):
    xml = xml or dump()
    return [t for t in re.findall(r'text="([^"]+)"', xml) if len(t) < 60]


def tap_text(text, retries=8):
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
                time.sleep(0.8)
                return True
        # substring match (e.g. 信任 in 信任并继续)
        for m in re.finditer(
            r'text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml
        ):
            if text in m.group(1):
                x1, y1, x2, y2 = map(int, m.groups()[1:])
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(0.8)
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


def accept_host_key(rounds=4):
    for i in range(rounds):
        xml = dump()
        if "未知主机密钥" not in xml and "信任并继续" not in xml:
            return True
        print(f"hostkey round {i}:", texts(xml)[:15], flush=True)
        if tap_text("信任并继续") or tap_text("信任"):
            time.sleep(1.5)
            shot(f"hk_accepted_{i}")
        else:
            # coords fallback: primary button often bottom-right in dialog
            adb("shell", "input", "tap", "780", "1500")
            time.sleep(1)
            shot(f"hk_fallback_{i}")
    return "未知主机密钥" not in dump()


shot("hk_start")
print("before", texts()[:40], flush=True)
print("accept", accept_host_key(), flush=True)
shot("after_trust")
print("after", texts()[:40], flush=True)

tap_text("会话") or tap_id("tab_sessions")
time.sleep(1)
shot("sessions_tab")
tap_id("session_refresh") or tap_text("刷新") or tap_text("Refresh")
adb("shell", "input", "swipe", "540", "500", "540", "1500", "400")
time.sleep(10)
accept_host_key()
time.sleep(6)
shot("sessions")
xml = dump()
print("sess", texts(xml)[:50], flush=True)

entered = False
for name in ["cliproxy", "tvapk", "e2e"]:
    if tap_id(f"session_enter_{name}"):
        entered = True
        print("enter tag", name, flush=True)
        break
if not entered and tap_text("进入"):
    entered = True
    print("enter text", flush=True)
print("ENTERED", entered, flush=True)
time.sleep(10)
accept_host_key()
time.sleep(5)
shot("terminal")
print("term", texts()[:40], flush=True)
adb("shell", "input", "tap", "540", "900")
time.sleep(0.3)
adb("shell", "input", "text", "echo e2e-pass4-ok")
adb("shell", "input", "keyevent", "66")
time.sleep(2)
shot("terminal_echo")
print("PASS4C_DONE", flush=True)
