# Remote Keyboard — Android Source Project

## What This Is
A single APK that lets one Android phone act as a Bluetooth keyboard for another.
No internet. No root. No Play Store required.

---

## Build Instructions (5 minutes)

### Requirements
- Android Studio (free): https://developer.android.com/studio
- Java 8 or later (bundled with Android Studio)

### Steps
1. Open Android Studio
2. Click "Open" → select this folder (RemoteKeyboard)
3. Wait for Gradle sync to complete (~2 min first time)
4. Click **Build → Build Bundle(s)/APK(s) → Build APK(s)**
5. APK appears in: `app/build/outputs/apk/debug/app-debug.apk`
6. Copy that APK to both phones

### Install on Phone
1. Transfer `app-debug.apk` to phone (USB, email, local wifi, etc.)
2. Settings → Security → Enable "Install unknown apps"
3. Open the APK file and install

---

## First-Time Setup (Both Phones)

### Enable the IME (once per phone)
Settings → System → Language & Input → On-screen keyboard → Manage keyboards → Enable **Remote Keyboard**

### Pair the Phones
Standard Android Bluetooth pairing — do this before launching the app.

---

## Usage

### Keyboard Phone (the one you type on)
1. Open app → **Keyboard Mode**
2. Select the target phone from the dropdown
3. Tap **Connect**

### Target Phone — Inject mode (keystrokes go into any app)
1. Open app → **Target Mode** → **Start Listening**
2. Open Settings → IME → Select **Remote Keyboard** as active keyboard
3. Open any app (WhatsApp, Notes, Browser...)
4. Tap a text field — now type on the keyboard phone!

### Target Phone — Teleprompter mode
1. Open app → **Teleprompter Mode** (auto-starts listening)
2. Keyboard phone connects and types
3. Text appears fullscreen in landscape

---

## Architecture

```
RemoteKeyboardIME (InputMethodService)
    ↕ broadcasts
BluetoothService (Foreground Service, RFCOMM)
    ↕ TCP-like stream over Bluetooth Classic
BluetoothService on target phone
    ↕ broadcasts
RemoteKeyboardIME → injects via InputConnection
```

## File Structure
```
app/src/main/
├── java/com/remotekeyboard/
│   ├── ime/RemoteKeyboardIME.kt      ← The keyboard
│   ├── bluetooth/BluetoothService.kt ← BT connection + heartbeat
│   ├── protocol/Command.kt           ← Wire encoding
│   └── ui/
│       ├── RoleSelectActivity.kt     ← Launch screen
│       ├── KeyboardActivity.kt       ← Keyboard phone UI
│       ├── TargetActivity.kt         ← Target phone UI
│       └── TeleprompterActivity.kt   ← Fullscreen display
├── res/layout/                       ← Screens
└── AndroidManifest.xml
```

## Limitations (Android by design)
- User must enable IME once in settings
- Can only inject into focused text fields
- One active BT target at a time
