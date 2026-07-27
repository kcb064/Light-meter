# Third Stop

*Play Store listing title: "Third Stop: Film Light Meter"*

An Android light meter for film photography — a handheld-meter replacement for
cameras with no working meter.

## Features

- **Reflective metering** through the phone camera: tap to spot-meter, or
  average the whole frame, reading the converged auto-exposure result.
- **Incident-style metering** using the ambient light sensor.
- Lock ISO and aperture to get the shutter speed (or lock shutter to get the
  aperture), in third stops, with ±3 EV exposure compensation.
- **Reciprocity failure correction** for common Ilford, Kodak and Fujifilm
  stocks — shows both metered and corrected exposure times.
- Reading log for recording what you shot.
- Calibration wizard: match the meter to a trusted reference meter or the
  sunny-16 rule; per-mode offsets.

## Building

```sh
./gradlew assembleDebug          # debug APK
./gradlew :core:test             # exposure-math unit tests (pure JVM)
```

The `:core` module (all exposure math) is pure Kotlin and can be built without
the Android SDK: set `LIGHTMETER_CORE_ONLY=1` to exclude the `:app` module.

Release signing reads `keystore.properties` (see `keystore.properties.example`)
or `KEYSTORE_*` environment variables; neither is committed.

## Releasing to Google Play

```sh
./gradlew bundleRelease        # app/build/outputs/bundle/release/app-release.aab
```

Play takes the `.aab`, not an APK. Without signing configured the bundle still
builds (useful in CI to catch R8 breakage) but is unsigned and cannot be
uploaded — create `keystore.properties` first, and keep the keystore backed up,
since losing it means losing the ability to ship updates.

Store listing assets, listing copy and the Play Console answers live in
[`store/`](store/README.md). The privacy policy Play requires is
[`docs/index.html`](docs/index.html), served via GitHub Pages.

## Accuracy notes

Phone auto-exposure systems vary by ±0.3–0.7 EV between devices, and ambient
light sensors are often coarsely quantized. Run the in-app calibration against
a trusted meter (or sunny 16) before relying on readings. Reciprocity data is
encoded from manufacturer datasheets as approximations — verify against the
current datasheet for critical work.
