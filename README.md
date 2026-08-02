# CAN Source Tester (debug APK)

A tiny diagnostic app for your Civic's Android head unit, built from what we
reverse-engineered out of the OEM Radio app's decompiled code
(`android.sourceservice.SourceInfo` and `android.carsource.McuManager`).

It does **not** contain any hardcoded CAN packet bytes — it calls the same
shared system services the Radio/USB apps call, via reflection, so it will
only work when installed **on your head unit itself** (those classes don't
exist on a normal phone/emulator).

## What it does

1. **Claim source** buttons (RADIO / USB / BT / AUX) call
   `SourceInfo.getInstance().onRequestPlayAudio(...)` — this is the exact
   call the OEM Radio app's `RadioUtils.requestPlayAudio()` makes to become
   the active source.
2. **Start/stop fake session** publishes a standard Android `MediaSessionCompat`
   with a title and an auto-incrementing position/duration, once per second —
   simulating what a real player reports for "now playing" timestamp info.
3. **Raw key inject** buttons call `McuManager.injectKeyEvent()` with the
   steering-wheel key codes for BT (`K_BT` / `K_BTMUSIC` / `K_MUSIC`), pulled
   straight from `McuConstant.java`. This tests whether the head unit's own
   firmware can switch the iMID to a BT-looking state at all, independent of
   any app logic — useful to isolate "firmware doesn't support it" vs.
   "app never asks for it."
4. Every action is logged on-screen (and to `adb logcat -s CanSourceTester`)
   so you can see exactly what succeeded, what threw an exception, and read
   the exception message — most head units guard these services with a
   signature-level permission, so if everything fails with a
   `SecurityException`, that's your answer right there (see below).

## How to build

You'll need Android Studio (this can't be compiled in a sandboxed
environment without network access to Google's Maven repo, so it's provided
as source only):

1. Open Android Studio → **Open** → select this `CanSourceTester` folder.
2. Let Gradle sync (it will auto-generate the Gradle wrapper).
3. Plug in your Android head unit via USB, enable USB debugging in its
   Developer Options, and click **Run ▶**. If ADB over USB isn't available
   on your unit, build an APK via **Build → Build Bundle(s)/APK(s) → Build
   APK(s)**, copy the `.apk` to a USB stick, and install it via a file
   manager on the head unit (you'll need "install from unknown sources"
   enabled).

## How to test

1. Open the app while some other source (e.g. real Bluetooth playback) is
   active, and note what the iMID currently shows.
2. Tap **BT** to claim source, then **Start fake session** with a test title.
   Watch the iMID. If it updates to show the title/timestamp — the shared
   service *can* display BT-style data, meaning your real Bluetooth app
   simply never calls this path (a fixable app-side gap). If nothing changes
   — the limitation is deeper (likely the CAN table itself, or a permission
   block).
3. Try the raw **K_BT / K_BTMUSIC / K_MUSIC** buttons independently (without
   the fake session) to see if the iMID reacts to just a "source switch" key
   with no accompanying app.
4. Watch the log panel and `adb logcat` for `SecurityException` — many head
   units restrict `android.sourceservice.*` and `android.carsource.*` to
   apps signed with the platform/system certificate. If you see this, no
   ordinary user-installed APK (including this one) can call these methods
   directly — you'd need to sign the app with the ROM's platform key
   (usually only obtainable from the unit's ROM dump), which is a much
   bigger undertaking than reflection alone can solve.

## Known limitation found during reverse engineering

`ISourceService` (the AIDL interface behind `SourceInfo`) only exposes
`onRequestPlayAudio(String)` and a **read-only** `getMusicInfo()` — there is
no method to directly push a filename/position/duration over that interface.
This strongly suggests the underlying system service pulls timestamp data
from a standard Android `MediaSession`, not from a custom push API — which
is why this debug app uses `MediaSessionCompat` for step 2 rather than
trying to call a (nonexistent) setter.
