# Rider AI — Android riding assistant

A handlebar-mounted phone becomes a safety assistant, heads-up display, dashcam and voice
co-pilot for motorcycle and scooter riders.

> **Riding aid, not a safety system.** Camera-based warnings, phone sensors and GPS can be
> late, wrong or missing. Ride as if every feature were switched off.

## Features

### Safety assist (NCAP-style warning functions)
Modelled on the driver-assist warnings that NCAP programmes test for, adapted to a phone:

| Function | How it works |
|---|---|
| **Forward Collision Warning** | ML Kit tracks objects seen by the phone's back camera (facing the road). Time-to-collision comes from how fast the object in your path grows in the image (a line fitted to 1/height reaches zero at impact). Warning below 2.7 s, "Brake!" below 1.5 s. Only active above 12 km/h. |
| **Headway monitoring** | Rough distance from the object's image height and the camera field of view; "Keep your distance" when the gap is under 1 s. |
| **Speed assist** | Voice and red speed readout above your set limit. |
| **Lean-angle alert** | Gravity sensor lean angle, with a "zero lean" calibration for your mount. Max lean left/right per trip. |
| **Crash detection + eCall-style SOS** | Impact spike above 4 g, then the bike lying on its side or the phone not moving. Saves the dashcam clip and starts a countdown you can cancel by voice ("I'm OK") or with a big button; then it texts your emergency contact a map link and opens the dialer. |

### HUD
High-contrast speed, heading, collision colour strip, time-to-collision / gap, lean gauge and
trip. **Mirror mode** flips the layout so it reads correctly reflected in a windscreen or HUD
combiner glass. Tap the screen to show the controls.

### Dashcam
- Loop recording in 1–15 min segments (default 3), oldest deleted past a storage budget.
- Each segment gets a `.srt` subtitle track with speed, GPS, lean and g-force, so any video
  player shows telemetry over the footage.
- **Save clip** (voice, button or automatic on crash) copies the current and previous segment
  to `Movies/RiderAI` in the gallery (telemetry to `Download/RiderAI`).
- Video only: the microphone stays free for voice commands.

### Voice commands
Hands-free with a wake word (default **"rider"**), or push-to-talk with the Talk button.

| Say | Does |
|---|---|
| "rider, save clip" / "mark" | Protect the footage |
| "rider, start / stop recording" | Dashcam on/off |
| "rider, HUD" / "camera view" / "mirror" | Switch display |
| "rider, speed" / "status" | Speed; trip, dashcam, GPS, max lean |
| "rider, mute" / "unmute" | Non-critical alerts off/on (brake warnings always sound) |
| "rider, SOS" / "I'm OK" | Start / cancel the emergency countdown |
| "rider, *anything else*" | Ask the AI co-pilot |

### AI co-pilot
Anything that is not a command goes to Claude (`claude-opus-5-5`) with live ride data
(speed, time, GPS, trip, lean) and the last few exchanges. Replies are kept to one or two
spoken sentences. Requests use low effort for quick answers and the server-side refusal
fallback. Add your API key in **Settings**; it is stored only on the phone.

## Build

Requirements: Android Studio (Ladybug or newer) or the Android SDK with API 35, JDK 17.

```bash
cd android-rider-ai
./gradlew testDebugUnitTest   # pure-Kotlin logic tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions (`.github/workflows/android-rider-ai.yml`) runs both and uploads the APK as
the `rider-ai-debug-apk` artifact.

Min Android 10 (API 29). Mount the phone in portrait with the back camera facing the road.

## Layout

```
app/src/main/java/com/riderai/
├── MainActivity.kt          permissions, keep-screen-on
├── RideViewModel.kt         wires sensors, safety, dashcam, voice and AI together
├── ai/RiderAssistant.kt     Claude co-pilot
├── safety/                  CollisionRisk (FCW/headway), CrashDetector, ML Kit analyzer, SOS
├── dashcam/                 loop recorder, SRT telemetry
├── voice/                   CommandParser, VoiceEngine (SpeechRecognizer + TTS)
├── sensors/                 GPS, lean / g-force
└── ui/                      camera view, HUD, settings
```

## Limitations and next steps
- ML Kit's built-in detector finds objects but does not label vehicles. For better FCW, swap
  in a COCO detector (e.g. EfficientDet-Lite) using `CustomObjectDetectorOptions` in
  `RoadObjectAnalyzer`.
- Distance and headway are estimates from a single camera. Set the camera's vertical field of
  view in Settings for better numbers.
- Recording stops when the screen turns off or the app is in the background; it resumes when
  you come back. A foreground service would be needed for background recording.
- Hands-free listening restarts the system recogniser, which beeps on some phones.
- Blind-spot and rear-collision warnings would need a second (rear) camera.
