# SK150C Control

Native Android app (Kotlin + Jetpack Compose) to control a **Wuzhi ZK‑SK150C**
DC buck-boost power supply over its Bluetooth communication module, using the
Modbus‑RTU protocol documented in the product's own manual.

## What it does

- Connects over Bluetooth to the ZK‑BT module plugged into the power supply.
- Polls voltage/current/power/temperature/protection status a few times a
  second and shows them live.
- Lets you set output voltage & current, turn the output on/off, lock the
  front panel, adjust backlight, toggle the buzzer, and recall memory groups
  M0–M10.

## Building it

This project was written and reviewed by hand but **not compiled**, because
the sandbox it was built in has no access to Google's Maven repository
(only a short allow-list of domains for npm/pip/crates/GitHub — no
`dl.google.com` / `maven.google.com`). You'll need to build it yourself:

1. Install [Android Studio](https://developer.android.com/studio) (current
   stable channel; anything from 2024.x on will work).
2. Open this folder (`sk150c/`) as an existing project — Studio will
   recognize the Gradle files and prompt to sync.
3. Let Gradle sync (this downloads the Android Gradle Plugin, Kotlin
   compiler, Compose libraries, etc. — needs internet access once).
4. Plug in an Android phone (USB debugging on) or use an emulator, and hit
   **Run**. Emulators can't do real Bluetooth, so for actual testing you'll
   need a physical phone.
5. Minimum Android version: **8.0 (API 26)**.

If Gradle sync complains about a specific version being unavailable, bump
the AGP/Kotlin/Compose BOM versions in `build.gradle.kts` /
`app/build.gradle.kts` to whatever Android Studio suggests — I pinned
versions that were current and mutually compatible as of early 2026, but
these move fast.

## ⚠️ The one thing I could not verify: BLE vs Classic Bluetooth

Your manual (pages 4 and 11) describes pairing through the "Wuzhi Zhilian"
(无治智联) app, which auto-discovers the module as "Wuzhi Power" when you
tap "Add Device" — it doesn't go through Android's normal Bluetooth pairing
screen. That strongly suggests the **ZK‑BT module is BLE** (a classic SPP
module would normally need standard OS-level pairing first), so I built the
app around BLE as the primary path.

But the manual doesn't state the module's exact chipset or GATT UUIDs, and I
have no way to test against real hardware from here. So the app hedges:

- **"Scan BLE"** — scans for BLE peripherals, and once connected, auto-detects
  a usable serial-like characteristic by trying known UUID families first
  (Nordic UART Service, then HM‑10/JDY‑08/AT‑09-style `FFE0/FFE1`, then
  `FFE5/FFE9/FFE4`), then falls back to a generic heuristic: any writable
  characteristic paired with any notifying characteristic in the same
  service.
- **"Paired (Classic)"** — if BLE doesn't work, pair the module with your
  phone the normal way in Android Bluetooth settings first, then use this
  button; the app will talk to it as a standard SPP serial port (the same
  approach HC‑05/HC‑06 modules use).

If BLE connects but nothing happens (no live readings), the auto-detected
characteristic is probably wrong for your specific module revision. The
most useful thing to do at that point is to sniff the "Wuzhi Zhilian" app's
BLE traffic with Android's built-in Bluetooth HCI snoop log (Developer
options → "Enable Bluetooth HCI snoop log", reproduce a connection with the
official app, pull `btsnoop_hci.log`, open in Wireshark) to get the exact
service/characteristic UUIDs, then hardcode them at the top of
`BleSerialLink.kt` (`KNOWN_UART_SERVICES`) ahead of the fallback list. I'm
glad to help with that log if you send it back.

## Protocol reference (from your manual)

- Transport: Modbus‑RTU, 11‑bit frames, no parity, over TTL serial carried by
  the Bluetooth module. Function codes supported by the device: `0x03`
  (read), `0x06` (write single register), `0x10` (write multiple registers).
- Slave address defaults to `1`.
- CRC16: standard Modbus polynomial `0xA001`, init `0xFFFF`, sent low byte
  first. Implemented in `modbus/Crc16.kt` and **verified against all four
  worked examples in your manual** (`01 03 00 00 00 10` → `44 06`, etc.) — see
  the register table transcription in `modbus/Registers.kt` for the full
  address map (V‑SET, I‑SET, VOUT, IOUT, POWER, UIN, AH/WH counters,
  temperatures, protection flags, memory groups M0–M10 at `0x0050 + n*0x0010`,
  and so on).

## Project layout

```
app/src/main/java/com/sk150c/control/
  modbus/     CRC16, frame encode/decode, register map, session (request/response)
  ble/        BLE + Classic Bluetooth transports behind a common SerialLink interface
  data/       Repository: polling loop, decode raw registers into UI state, commands
  ui/         Compose screens: scan/connect, live dashboard
```

## Changelog

**Update 3 (real hardware log — write characteristic was wrong):** A full
logcat from a real ZK-BT module connection showed the exact GATT property
byte for every characteristic in the `FFF0` service:

```
0000fff1(props=20) = NOTIFY + WRITE_NO_RESPONSE
0000fff2(props=18) = NOTIFY + READ            <- no WRITE property at all
0000fff3(props=4)  = WRITE_NO_RESPONSE
```

`KNOWN_UART_SERVICES` had `fff2` pinned as the write target (based on an
earlier, incorrect assumption). Since `fff2` has no write property,
`writeCharacteristic()` was returning failure **synchronously** on every
single call — not a timeout, an immediate rejection by the Android BLE
stack before anything was even sent over the air. That's why every poll and
every command in the log failed instantly instead of timing out normally.

Fixed: the write target is now `fff3` (the characteristic that actually has
`WRITE_NO_RESPONSE`). Notify stays on `fff1`, which was already correct.

**Update 2 (full logging added):** A second hardware log showed BLE connect,
service discovery, `setCharacteristicNotification()`, and `configureMTU()`
all succeeding — but **no `writeCharacteristic()` or `writeDescriptor()`
line ever appeared**, even though those are framework-level Android debug
logs (same style/tag as the others that did show up). Two conclusions:

1. The notify characteristic (`fff1`) has **no CCCD (0x2902) descriptor**,
   so the app's earlier attempt to enable notifications never actually sent
   anything to the device.
2. The polling loop's read request **never actually reached
   `BluetoothGatt.writeCharacteristic()`** — but the app's own diagnostics
   (the "Connected: service..." banner) only appeared in the in-app UI, not
   in logcat, so there was no way to see *why* from the log alone.

Added proper `Log.d`/`Log.w`/`Log.e` instrumentation (tags `BleSerialLink`,
`SK150C-Repo`, `SK150C-Modbus`) covering: every BLE connection/service/MTU/
descriptor callback with the full characteristic+property dump, every byte
chunk sent and received (hex), and every polling-loop attempt/timeout/
exception. **If you hit "No response from device" again, capture logcat
filtered to those three tags (or just send the whole thing) — it will now
show exactly which step breaks.**

**Update 1 (confirmed against real hardware log):** a logcat capture showed
the app *did* connect over BLE and successfully found a working
characteristic — `0000fff1-0000-1000-8000-00805f9b34fb` — which is the
`FFF0` service / `FFF1` notify / `FFF2` write family, common on cheap
Telink-based BLE UART bridges. That family is now pinned explicitly in
`KNOWN_UART_SERVICES` instead of relying only on the heuristic fallback.
Also switched to `WRITE_TYPE_NO_RESPONSE` when available (several cheap
"transparent UART" bridges advertise write-with-response but silently drop
those frames), added a write-queue stall guard, a 400ms settle delay before
the first poll, and a longer 3s per-request timeout.

## Known limitations / things I'd add next

- Only the "main block" of registers (`0x0000`–`0x001E`) is polled; the
  per‑memory‑group protection settings (`S‑LVP`, `S‑OVP`, etc. at `0x0050+`)
  are defined in `Registers.kt` (`GroupReg`) but have no editing UI yet —
  only quick‑recall via `EXTRACT_M`.
  Recall is not the same as edit — right now you can *load* M0–M10, but not
  *edit and save* a group's stored values from the app.
  Calibration (`0x0010`/`0x0012` calibration dialogs from the manual) and
  address/baud‑rate configuration registers aren't exposed either.
- No persistence of "last connected device" — you reconnect every launch.
- BLE MTU negotiation isn't requested; writes are chunked to 20 bytes for
  broad compatibility, which works but is not maximally efficient.
