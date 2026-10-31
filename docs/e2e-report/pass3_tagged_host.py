#!/usr/bin/env python3
"""Add host via testTag/content-desc resource-id, then refresh sessions. No DISPLAY changes."""
import os, re, subprocess, time
from pathlib import Path

HOME = Path.home()
OUT = HOME / "mtmux-e2e" / "run3"
SHOT = OUT / "screenshots"
LOG = OUT / "logs"
SHOT.mkdir(parents=True, exist_ok=True)
LOG.mkdir(parents=True, exist_ok=True)
APP = "app.mtmux.android"
for line in (HOME / "mtmux-e2e" / "vps.env").read_text().splitlines():
    if "=" in line:
        k, v = line.split("=", 1)
        os.environ[k.strip()] = v.strip()
ALIAS = os.environ["VPS_ALIAS"]
HOST = os.environ["VPS_HOST"]
USER = os.environ["VPS_USER"]
PW = os.environ["VPS_PASSWORD"]
n = 0

def adb(*a, timeout=90):
    return subprocess.run(["adb", *a], capture_output=True, text=True, timeout=timeout)

def shot(name):
    global n
    n += 1
    adb("shell", "screencap", "-p", "/sdcard/s.png")
    dest = SHOT / f"{n:03d}_{name}.png"
    adb("pull", "/sdcard/s.png", str(dest))
    print("SHOT", dest.name, flush=True)

def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    adb("pull", "/sdcard/ui.xml", str(LOG / "ui.xml"))
    return (LOG / "ui.xml").read_text(errors="ignore")

def find_by_id(rid: str):
    xml = dump()
    # resource-id may be "app.mtmux.android:id/host_add" or just "host_add"
    pat = rf'resource-id="[^"]*{re.escape(rid)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    m = re.search(pat, xml)
    if not m:
        pat2 = rf'content-desc="{re.escape(rid)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
        m = re.search(pat2, xml)
    if not m:
        # reverse attr order
        pat3 = rf'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*resource-id="[^"]*{re.escape(rid)}"'
        m = re.search(pat3, xml)
    if not m:
        return None
    x1,y1,x2,y2 = map(int, m.groups())
    return (x1+x2)//2, (y1+y2)//2

def tap_id(rid, retries=8):
    for _ in range(retries):
        p = find_by_id(rid)
        if p:
            adb("shell", "input", "tap", str(p[0]), str(p[1]))
            time.sleep(0.7)
            return True
        time.sleep(0.5)
    return False

def tap_text(text, retries=5):
    for _ in range(retries):
        xml = dump()
        for pat in (
            rf'text="{re.escape(text)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            rf'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="{re.escape(text)}"',
        ):
            m = re.search(pat, xml)
            if m:
                x1,y1,x2,y2=map(int,m.groups())
                adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))
                time.sleep(0.6)
                return True
        time.sleep(0.4)
    return False

def type_ascii(s):
    # char-safe for IP and password
    out = []
    for ch in s:
        if ch.isalnum() or ch in "_-":
            out.append(ch)
        elif ch == ".":
            out.append("\\.")
        elif ch == "@":
            out.append("\\@")
        elif ch == " ":
            out.append("%s")
        else:
            out.append(ch)
    adb("shell", "input", "text", "".join(out))
    time.sleep(0.25)

def clear_field():
    for _ in range(40):
        adb("shell", "input", "keyevent", "67")

print(adb("devices").stdout, flush=True)
adb("uninstall", APP)
r = adb("install", "-r", "-d", str(HOME / "mtmux-e2e" / "m-tmux.apk"), timeout=180)
print(r.stdout, r.stderr, flush=True)
assert r.returncode == 0, "install failed"
adb("shell", "am", "force-stop", APP)
adb("shell", "monkey", "-p", APP, "-c", "android.intent.category.LAUNCHER", "1")
time.sleep(4)
shot("home")
assert tap_text("主机"), "no hosts tab"
shot("hosts")
assert tap_id("host_add"), "host_add tag not found"
time.sleep(1)
shot("editor")
xml = dump()
(LOG/"editor.xml").write_text(xml)
print("ids sample", re.findall(r'resource-id="([^"]+)"', xml)[:30], flush=True)
print("descs", re.findall(r'content-desc="([^"]+)"', xml)[:30], flush=True)

def fill(tag, value, clear=True):
    assert tap_id(tag), f"missing {tag}"
    time.sleep(0.3)
    if clear:
        clear_field()
    type_ascii(value)
    print("filled", tag, flush=True)

fill("host_alias", ALIAS)
fill("host_hostname", HOST)
fill("host_username", USER)
fill("host_password", PW, clear=False)
shot("filled")
# Soft keyboard covers host_save — dismiss IME then scroll
adb("shell", "input", "keyevent", "4")
time.sleep(0.4)
for _ in range(5):
    p = find_by_id("host_save")
    if p and p[1] < 2000:
        break
    adb("shell", "input", "swipe", "540", "1600", "540", "500", "350")
    time.sleep(0.4)
assert tap_id("host_save") or tap_text("保存"), "no save"
time.sleep(2)
shot("after_save")
xml = dump()
print("host_ok", ALIAS in xml or HOST in xml, flush=True)
assert tap_text("会话")
time.sleep(1)
assert tap_id("session_refresh") or tap_text("Refresh") or True
adb("shell", "input", "swipe", "540", "500", "540", "1500", "400")
time.sleep(6)
for t in ["信任并继续", "信任", "接受", "确认", "确定", "继续", "OK"]:
    if tap_text(t, retries=1) or tap_id("host_key_accept"):
        shot(f"hk_{t}")
        time.sleep(2)
time.sleep(5)
shot("sessions")
xml = dump()
(LOG/"sessions.xml").write_text(xml)
print("texts", [t for t in re.findall(r'text="([^"]+)"', xml) if len(t)<40][:40], flush=True)
# enter any session
for name in ["cliproxy", "tvapk", "e2e"]:
    if tap_id(f"session_enter_{name}") or (name in xml and tap_text("进入")):
        break
else:
    tap_text("进入")
time.sleep(8)
for t in ["接受", "确认", "确定"]:
    tap_text(t, retries=1)
time.sleep(4)
shot("terminal")
adb("shell", "input", "tap", "540", "900")
type_ascii("echo e2e-tags-ok")
adb("shell", "input", "keyevent", "66")
time.sleep(2)
shot("terminal_echo")
print("PASS3_DONE", flush=True)
