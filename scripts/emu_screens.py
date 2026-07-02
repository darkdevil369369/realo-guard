#!/usr/bin/env python3
"""Drive the REALO Guard app on an emulator via adb/uiautomator and take Play screenshots.
Runs inside GitHub Actions (KVM emulator). Taps are resolved from uiautomator dumps by text."""
import subprocess, time, re, sys, os

OUT = "shots"
EMAIL = "playstore.demo@tryrealo.com"
PASSWORD = "RealoDemo26"
PKG = "com.realo.guard"

def sh(cmd, **kw):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, **kw)

def adb(cmd):
    return sh(f"adb {cmd}")

def dump():
    adb("shell uiautomator dump /sdcard/ui.xml")
    return adb("shell cat /sdcard/ui.xml").stdout

def find_bounds(xml, text, exact=False):
    """center of the first node whose text or content-desc contains `text`."""
    pat = re.escape(text)
    for m in re.finditer(r'<node[^>]*/>|<node[^>]*>', xml):
        node = m.group(0)
        tm = re.search(r'text="([^"]*)"', node)
        dm = re.search(r'content-desc="([^"]*)"', node)
        val = (tm.group(1) if tm else "") + " " + (dm.group(1) if dm else "")
        ok = (val.strip() == text) if exact else (text.lower() in val.lower())
        if ok:
            bm = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
            if bm:
                x1, y1, x2, y2 = map(int, bm.groups())
                if x2 > x1 and y2 > y1:
                    return ((x1 + x2) // 2, (y1 + y2) // 2)
    return None

def tap_text(text, tries=6, exact=False):
    for i in range(tries):
        c = find_bounds(dump(), text, exact)
        if c:
            adb(f"shell input tap {c[0]} {c[1]}")
            time.sleep(1.2)
            return True
        time.sleep(2)
    print(f"!! could not find '{text}'"); return False

def type_text(s):
    adb(f"shell input text '{s.replace(' ', '%s')}'")
    time.sleep(.6)

def shot(name):
    time.sleep(1.5)
    adb(f"shell screencap -p /sdcard/{name}.png")
    adb(f"pull /sdcard/{name}.png {OUT}/{name}.png")
    print("shot:", name)

def main():
    os.makedirs(OUT, exist_ok=True)
    adb("shell settings put global window_animation_scale 0")
    adb("shell settings put global transition_animation_scale 0")
    adb("shell settings put global animator_duration_scale 0")
    # grant notification access BEFORE login so home shows the protected dashboard
    adb(f"shell cmd notification allow_listener {PKG}/.service.ScanListenerService")
    adb(f"shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1")
    time.sleep(6)

    # ---- login ----
    tap_text("Log in")                      # mode toggle
    time.sleep(1)
    if tap_text("Email"):
        type_text(EMAIL)
    adb("shell input keyevent 111")         # ESC to close keyboard suggestions
    if tap_text("Password"):
        type_text(PASSWORD)
    adb("shell input keyevent 111")
    # the button also says "Log in" — tap the LAST match: use exact dump order fallback
    xml = dump()
    matches = [m for m in re.finditer(r'<node[^>]*text="Log in"[^>]*>', xml)]
    if matches:
        bm = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', matches[-1].group(0))
        if bm:
            x1, y1, x2, y2 = map(int, bm.groups())
            adb(f"shell input tap {(x1+x2)//2} {(y1+y2)//2}")
    time.sleep(8)                           # network login

    # ---- Guard home (protected dashboard) ----
    shot("1_guard_home")

    # ---- quick check cards -> Tools WebView ----
    if tap_text("Photo", exact=False):
        time.sleep(10)                      # webview load
        shot("2_photo_check")
    # back to Guard
    tap_text("Guard", exact=True)
    time.sleep(2)
    if tap_text("Message", exact=False):
        time.sleep(10)
        shot("3_message_scan")
    tap_text("Guard", exact=True)
    time.sleep(2)
    if tap_text("Payment", exact=False):
        time.sleep(10)
        shot("4_pay_safe")
    # Tools tab full view
    tap_text("Tools", exact=True)
    time.sleep(8)
    shot("5_tools")
    print("DONE")

if __name__ == "__main__":
    main()
