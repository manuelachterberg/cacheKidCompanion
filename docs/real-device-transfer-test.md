# Real-Device Mission Transfer Test

This runbook verifies the explicit-address MVP transfer path from a host app instance to a Meebook kid app instance.

It is the acceptance procedure for issue #42 and the preparation step for the end-to-end validation in #39.

## Scope

This procedure tests real local-network transfer. It must not be confused with ADB sideload.

In scope:

- Android prototype host app sending one prepared mission package
- Meebook kid app receiver mode on the same local network
- `POST /missions` transfer to the kid receiver
- kid-side import, local persistence, app restart, and offline mission loading
- expected UI and log states for success and common failures

Out of scope:

- automatic discovery
- pairing
- QR-code setup
- iPhone production host implementation
- ADB sideload as the product transfer path

## Required Setup

Devices:

- one host device running the current Android prototype host flow
- one Meebook kid device running the current kid app

Network:

- both devices must be on the same Wi-Fi or hotspot network
- client isolation must be disabled on the network
- the host must be able to reach the kid device on TCP port `8765`
- the app intentionally permits cleartext HTTP because the MVP receiver uses local `http://<kid-ip>:8765/missions`

Build:

```sh
JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew assembleDebug
```

Install the same debug build on both roles when testing the Android prototype flow:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If multiple devices are connected, pass `-s <serial>` to each `adb` command.

## Find the Kid Receiver Address

The receiver listens on port `8765`.

Current limitation: the app shows `Empfang aktiv auf Port 8765.`, but it does not yet display the device IP address. Until discovery or IP display exists, get the kid device address from Android settings, router UI, or ADB.

ADB option:

```sh
adb -s <kid-serial> shell ip -f inet addr show wlan0
```

Use the `inet` address without the CIDR suffix.

Example:

```text
inet 192.168.178.57/24
```

Host input:

```text
Empfangsadresse: 192.168.178.57
Port: 8765
```

Do not use `127.0.0.1` for real device-to-device transfer. That points to the host device itself.

## Prepare the Kid Device

1. Start the app on the Meebook.
2. If a mission is currently open, tap the home button to return to the menu.
3. Verify the receiver panel is visible.
4. Tap `Empfang starten`.
5. Expected status: `Empfang aktiv auf Port 8765.`

The receiver panel is hidden while a mission is actively open. Return home before starting receive mode.

## Prepare the Host Mission

Use the Android prototype host flow:

1. Import or manually create a mission.
2. Confirm title, summary, and target coordinates.
3. Tap `Missionsdaten uebernehmen` if fields were edited.
4. Enter the kid receiver address.
5. Keep port `8765`.

The host accepts these address forms:

- `192.168.178.57`
- `192.168.178.57:8765`
- `http://192.168.178.57:8765/missions`
- `kid-reader.local`

The configured port field remains the source of truth. If a pasted URL contains another port, the port field is still used.

## Execute Transfer

1. On the kid device, keep receiver mode running.
2. On the host device, tap `Mission senden`.
3. Wait for the send result.

Expected host success:

- send result is successful
- message contains `Mission empfangen`
- receiver status is `IMPORTED` in logs or debug output when inspected

Expected kid success:

- receiver status changes to `Mission empfangen: <mission-id>`
- mission opens or can be loaded from the menu
- route, player marker `O`, target `X`, and offline map base render

## Restart Persistence Check

After a successful transfer:

1. Force-stop or close the kid app.
2. Start the kid app again.
3. Tap the home button if the mission screen is already active.
4. Tap `Mission laden` if needed.

Expected result:

- the transferred mission is still available locally
- the mission can be opened without repeating the transfer
- the app does not require ADB sideload to recover the mission

## Expected Failure States

Host-side expected failures:

| Condition | Expected result |
| --- | --- |
| Blank address | `Empfangsadresse fehlt.` |
| Invalid port | `Port ist ungueltig.` |
| Wrong IP or receiver stopped | connection failure message |
| Cleartext HTTP blocked | app build is missing the intentional local-transfer cleartext permission |
| Kid rejects package | HTTP error with the kid receiver message |
| Timeout | timeout message |

Kid receiver expected failures:

| Condition | Receiver status |
| --- | --- |
| wrong endpoint | `UNSUPPORTED_ENDPOINT` |
| missing `Content-Length` | `MISSING_LENGTH` |
| invalid `Content-Length` | `INVALID_LENGTH` |
| wrong `Content-Type` | `UNSUPPORTED_MEDIA_TYPE` |
| empty request body | `EMPTY_BODY` |
| incomplete body | `INCOMPLETE_BODY` |
| invalid zip | `INVALID_ZIP` |
| invalid manifest | `INVALID_MANIFEST` |
| validation failure | `VALIDATION_FAILED` |
| local store failure | `STORE_FAILED` |
| successful import | `IMPORTED` |

## Useful Logs

Kid receiver logs:

```sh
adb -s <kid-serial> logcat | grep -E "CacheKid|Mission|Receiver"
```

Host transfer logs:

```sh
adb -s <host-serial> logcat | grep -E "CacheKid|Mission|Sender|Resolver"
```

Network reachability from a development machine, if it is on the same network:

```sh
nc -vz <kid-ip> 8765
```

## ADB Sideload Fallback

ADB sideload is a development fallback only. It is useful when testing kid-side map and navigation behavior while transfer is broken, but it does not satisfy the real transfer acceptance criteria.

Use sideload only to isolate whether a failure is in transfer or in mission import/rendering.

Expected distinction:

- real transfer: host sends over local network to kid receiver
- ADB sideload: developer pushes a ZIP into the kid app sideload directory and taps `ADB-Sideload laden`

If ADB sideload works but real transfer fails, continue debugging #30/#39 transfer behavior rather than closing the transfer validation.

## Result Recording

For #39, record:

- host device model
- kid device model
- network type: Wi-Fi or hotspot
- kid IP address used
- mission ID
- whether the mission survived app restart
- whether the mission opened offline
- any host message or kid receiver status for failures

Post the result on #30 or the #39 pull request.
