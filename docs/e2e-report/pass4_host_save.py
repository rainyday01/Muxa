#!/usr/bin/env python3
"""Add host via testTags; dismiss IME + scroll so host_save is tappable; verify persist."""
import os
import re
import subprocess
import time
from pathlib import Path

HOME = Path.home()
OUT = HOME / "mtmux-e2e" / "run4"
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
    for pat in (
        rf'resource-id="[^"]*{re.escape(rid)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        rf'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*resource-id="[^"]*{re.escape(rid)}"',
        rf'content-desc="{re.escape(rid)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        rf'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*content-desc="{re.escape(rid)}"',
    ):
        m = re.search(pat, xml)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            return (x1 + x2) // 2, (y1 + y2) // 2, y1, y2
    return None


def tap_id(rid, retries=8):
    for _ in range(retries):
        p = find_by_id(rid)
        if p:
            adb("shell", "input", "tap", str(p[0]), str(p[1]))
            time.sleep(0.7)
            return True
        time.sleep(0.4)
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
                x1, y1, x2, y2 = map(int, m.groups())
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(0.6)
                return True
        time.sleep(0.4)
    return False


def type_ascii(s):
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
            # adb input text is fragile; percent-encode uncommon chars
            out.append(f"%{ord(ch):02x}" if ord(ch) < 128 else ch)
    adb("shell", "input", "text", "".join(out))
    time.sleep(0.3)


def clear_field(n=50):
    for _ in range(n):
        adb("shell", "input", "keyevent", "67")


def hide_ime():
    """Dismiss soft keyboard without leaving the editor if possible."""
    # KEYCODE_BACK closes IME first when focused
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.5)
    # Also try hide-soft-input via window manager (no-op if already hidden)
    adb("shell", "input", "keyevent", "111")  # ESC
    time.sleep(0.3)


def scroll_to_save(max_swipes=6):
    """Scroll host editor until host_save is on-screen (y roughly < 2000)."""
    for i in range(max_swipes):
        p = find_by_id("host_save")
        if p:
            cx, cy, y1, y2 = p
            # visible area roughly above bottom nav (~2100)
            if y1 < 2000 and y2 > 200:
                print(f"save visible swipe={i} y={y1}-{y2}", flush=True)
                return True
        # swipe form up
        adb("shell", "input", "swipe", "540", "1600", "540", "500", "350")
        time.sleep(0.5)
    return find_by_id("host_save") is not None


def fill(tag, value, clear=True):
    assert tap_id(tag), f"missing {tag}"
    time.sleep(0.35)
    if clear:
        clear_field()
    type_ascii(value)
    print("filled", tag, flush=True)


print(adb("devices").stdout, flush=True)
# fresh data so we verify real save
adb("shell", "pm", "clear", APP)
r = adb("install", "-r", "-d", str(HOME / "mtmux-e2e" / "m-tmux.apk"), timeout=180)
print("install", r.returncode, r.stdout, r.stderr, flush=True)
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
(LOG / "editor.xml").write_text(xml)
print("ids", re.findall(r'resource-id="([^"]+)"', xml)[:25], flush=True)

fill("host_alias", ALIAS)
fill("host_hostname", HOST)
fill("host_username", USER)
fill("host_password", PW, clear=False)
shot("filled")

hide_ime()
shot("ime_hidden")
assert scroll_to_save(), "host_save not found after scroll"
shot("scrolled_to_save")
assert tap_id("host_save") or tap_text("保存"), "could not tap save"
time.sleep(2.5)
shot("after_save")
xml = dump()
(LOG / "after_save.xml").write_text(xml)
# leave editor if still open
if "screen_host_editor" in xml or "添加主机" in xml or "host_save" in xml:
    print("WARN still on editor, retry save", flush=True)
    hide_ime()
    scroll_to_save()
    tap_id("host_save") or tap_text("保存")
    time.sleep(2)
    shot("after_save_retry")
    xml = dump()

# navigate to hosts list
if "tencentvps" not in xml and "Hosts" not in xml:
    tap_id("host_editor_back") or adb("shell", "input", "keyevent", "4")
    time.sleep(1)
    tap_text("主机")
    time.sleep(1)
shot("hosts_list")
xml = dump()
(LOG / "hosts_list.xml").write_text(xml)
host_ok = ALIAS in xml or HOST in xml
print("HOST_SAVED", host_ok, flush=True)
print("texts", [t for t in re.findall(r'text="([^"]+)"', xml) if len(t) < 40][:40], flush=True)
assert host_ok, "host not in list after save"

def accept_host_key(rounds=4):
    """App dialog uses 信任并继续 / 拒绝 (not 接受)."""
    for i in range(rounds):
        xml = dump()
        if "未知主机密钥" not in xml and "信任并继续" not in xml:
            return True
        if tap_text("信任并继续", retries=2) or tap_text("信任", retries=1):
            shot(f"hk_{i}")
            time.sleep(1.5)
        else:
            break
    return "未知主机密钥" not in dump()


# probe optional (may open host-key dialog)
if tap_text("环境检查", retries=2):
    time.sleep(8)
    accept_host_key()
    shot("probe")

# sessions refresh
for _ in range(3):
    if tap_text("会话") or tap_id("tab_sessions"):
        break
    accept_host_key()
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.5)
time.sleep(1)
tap_id("session_refresh") or True
adb("shell", "input", "swipe", "540", "500", "540", "1500", "400")
time.sleep(8)
accept_host_key()
time.sleep(5)
shot("sessions")
xml = dump()
(LOG / "sessions.xml").write_text(xml)
print("sess_texts", [t for t in re.findall(r'text="([^"]+)"', xml) if len(t) < 40][:40], flush=True)

entered = False
for name in ["cliproxy", "tvapk", "e2e"]:
    if tap_id(f"session_enter_{name}"):
        entered = True
        break
    if name in xml and tap_text("进入", retries=2):
        entered = True
        break
if not entered:
    entered = bool(tap_text("进入", retries=2))
print("ENTERED", entered, flush=True)
time.sleep(8)
accept_host_key()
time.sleep(4)
shot("terminal")
adb("shell", "input", "tap", "540", "900")
type_ascii("echo e2e-pass4-ok")
adb("shell", "input", "keyevent", "66")
time.sleep(2)
shot("terminal_echo")

# reopen persist check
adb("shell", "am", "force-stop", APP)
adb("shell", "monkey", "-p", APP, "-c", "android.intent.category.LAUNCHER", "1")
time.sleep(3)
tap_text("主机")
time.sleep(1)
shot("reopen_hosts")
xml = dump()
persist = ALIAS in xml or HOST in xml
print("PERSIST", persist, flush=True)
assert persist, "host not persisted after restart"
print("PASS4_DONE", flush=True)
