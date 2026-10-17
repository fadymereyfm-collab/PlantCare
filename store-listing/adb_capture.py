"""
PlantCare — Real Screenshot Capture via ADB
============================================
Captures 8 real screenshots from a connected Android emulator or device.

Prerequisites:
  1. Android emulator running (API 24+, resolution 1080x1920)
     Android Studio → Device Manager → Launch emulator
  2. App installed in dev or prod flavor:
       ./gradlew installDevDebug      (dev flavor)
       ./gradlew installProdRelease   (prod flavor)
  3. adb on PATH (usually: C:/Users/<you>/AppData/Local/Android/Sdk/platform-tools/)

Run:
  python adb_capture.py [--pkg com.fadymerey.plantcare.dev]

The script:
  1. Verifies a device is connected
  2. Launches the app
  3. Waits for each screen to load
  4. Saves screenshots to store-listing/screenshots/ (overwriting placeholders)
"""

import subprocess, time, sys, os, argparse

SCREENSHOTS_DIR = os.path.join(os.path.dirname(__file__), "screenshots")
DEFAULT_PKG     = "com.fadymerey.plantcare.dev"
MAIN_ACTIVITY   = "com.example.plantcare.ui.onboarding.OnboardingActivity"


def run(cmd, check=True):
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if check and result.returncode != 0:
        print(f"  ERROR: {cmd}\n  {result.stderr.strip()}")
        sys.exit(1)
    return result.stdout.strip()


def adb(cmd, check=True):
    return run(f"adb {cmd}", check=check)


def check_device():
    out = adb("devices")
    lines = [l for l in out.splitlines() if "\tdevice" in l]
    if not lines:
        print("❌  No Android device/emulator found.")
        print("    Start an emulator in Android Studio → Device Manager, then retry.")
        sys.exit(1)
    print(f"✓  Device found: {lines[0].split()[0]}")


def launch_app(pkg):
    adb(f"shell am force-stop {pkg}", check=False)
    time.sleep(1)
    adb(f"shell am start -n {pkg}/{MAIN_ACTIVITY}")
    time.sleep(3)


def tap(x, y):
    adb(f"shell input tap {x} {y}")
    time.sleep(1.5)


def swipe(x1, y1, x2, y2, dur=300):
    adb(f"shell input swipe {x1} {y1} {x2} {y2} {dur}")
    time.sleep(1.5)


def screenshot(name):
    remote = f"/sdcard/plantcare_{name}.png"
    local  = os.path.join(SCREENSHOTS_DIR, f"{name}_1080x1920.png")
    adb(f"shell screencap -p {remote}")
    adb(f"pull {remote} \"{local}\"")
    adb(f"shell rm {remote}")
    size = os.path.getsize(local) // 1024
    print(f"  ✓  {name}_1080x1920.png  ({size} KB)")


def skip_onboarding_if_needed():
    """If onboarding is showing, skip to MainActivity."""
    # Tap Skip button (last page jump) then Guest mode
    tap(540, 1750)   # btnSkip — approximate centre of skip button
    time.sleep(2)
    tap(540, 1750)   # btnGuestMode (last page)
    time.sleep(2)
    # Consent dialog — tap "Nur notwendige" (decline analytics for clean state)
    tap(540, 1600)
    time.sleep(2)


def navigate_to_tab(index):
    """Bottom nav tabs: 0=Alle, 1=Meine, 2=Kalender, 3=Heute"""
    tab_x = [135, 405, 675, 945]
    tap(tab_x[index], 1820)


def main(pkg):
    os.makedirs(SCREENSHOTS_DIR, exist_ok=True)
    print(f"\nPlantCare Screenshot Capture")
    print(f"Package : {pkg}")
    print(f"Output  : {SCREENSHOTS_DIR}\n")

    check_device()
    print("\nLaunching app…")
    launch_app(pkg)
    skip_onboarding_if_needed()
    time.sleep(2)

    # ── 01 Alle Pflanzen ──────────────────────────────────────
    print("\n[1/8] Alle Pflanzen tab")
    navigate_to_tab(0)
    time.sleep(1)
    screenshot("01_alle_pflanzen")

    # ── 02 Meine Pflanzen ─────────────────────────────────────
    print("[2/8] Meine Pflanzen tab")
    navigate_to_tab(1)
    time.sleep(1)
    screenshot("02_meine_pflanzen")

    # ── 03 Kalender ───────────────────────────────────────────
    print("[3/8] Kalender tab")
    navigate_to_tab(2)
    time.sleep(1)
    screenshot("03_kalender")

    # ── 04 Heute ─────────────────────────────────────────────
    print("[4/8] Heute tab")
    navigate_to_tab(3)
    time.sleep(1)
    screenshot("04_heute")

    # ── 05 Pflanzendetail ────────────────────────────────────
    print("[5/8] Plant detail (tap first plant in Meine Pflanzen)")
    navigate_to_tab(1)
    time.sleep(1)
    tap(540, 450)   # tap first plant card
    time.sleep(2)
    screenshot("05_detail")
    adb("shell input keyevent KEYCODE_BACK")
    time.sleep(1)

    # ── 06 Erinnerungen (Kalender day tap) ───────────────────
    print("[6/8] Reminders list (Kalender day with dots)")
    navigate_to_tab(2)
    time.sleep(1)
    # Tap a day that has a reminder dot — approximate position
    tap(810, 620)
    time.sleep(2)
    screenshot("06_erinnerungen")
    adb("shell input keyevent KEYCODE_BACK", check=False)
    time.sleep(1)

    # ── 07 KI-Erkennung ──────────────────────────────────────
    print("[7/8] Plant identification screen")
    navigate_to_tab(0)
    time.sleep(1)
    # FAB or identify button — approximate; may need adjustment
    tap(990, 1750)
    time.sleep(2)
    screenshot("07_ki_erkennung")
    adb("shell input keyevent KEYCODE_BACK", check=False)
    time.sleep(1)

    # ── 08 Einstellungen ────────────────────────────────────
    print("[8/8] Settings dialog")
    navigate_to_tab(1)
    time.sleep(1)
    # Toolbar settings icon (top-right area)
    tap(990, 136)
    time.sleep(2)
    screenshot("08_einstellungen")
    adb("shell input keyevent KEYCODE_BACK", check=False)

    print(f"\n✓  All 8 screenshots saved to {SCREENSHOTS_DIR}")
    print("   Review them, then upload to Play Console → Grafiken → Screenshots.\n")
    print("NOTE: If any screen didn't capture correctly, adjust the tap(x, y)")
    print("      coordinates in this script to match your emulator layout.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Capture PlantCare store screenshots via ADB")
    parser.add_argument("--pkg", default=DEFAULT_PKG,
                        help=f"App package name (default: {DEFAULT_PKG})")
    args = parser.parse_args()
    main(args.pkg)
