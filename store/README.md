# Play Store listing assets

Regenerate the graphics with `python store/generate_assets.py`.

| File | Play slot | Spec |
| --- | --- | --- |
| `play-icon-512.png` | App icon | 512×512, 32-bit PNG |
| `play-feature-graphic-1024x500.png` | Feature graphic | 1024×500, 24-bit PNG |
| `screenshots/*.png` | Phone screenshots | 1080×1920 (9:16) |

The graphics reuse the launcher icon's motif — viewfinder brackets around a
spot-meter circle. If `res/drawable/ic_launcher_foreground.xml` changes, update
`generate_assets.py` to match.

Screenshots were captured on the `lightmeter` AVD with the display forced to
1080×1920, because the AVD's native 1080×2400 is a 2.22:1 ratio and Play
rejects phone screenshots above 2:1. Play shows them in upload order, so the
numbering is the intended running order:

1. `01-meter` — reflective metering, zoom / focal-length readout
2. `02-incident` — incident metering off the ambient light sensor
3. `03-film` — film stock picker
4. `04-reciprocity` — long exposure corrected for the chosen stock
5. `05-log` — reading log
6. `06-settings` — per-mode calibration offsets
7. `07-calibration` — calibration wizard
8. `08-lens` — matching the meter to your camera's lens

> **Retake `01-meter.png` on a real phone before publishing.** It is the only
> shot that shows the camera viewfinder, and on the emulator that viewfinder is
> Android's synthetic test scene — a low-res cartoon house. A real device also
> reports real lenses, so it is the only way to show the zoom / focal-length
> readout honestly. The other shots are emulator-independent.

## Adding screenshots from a phone

Take the shot on the phone, get the file onto this machine, then:

```sh
python store/prepare_screenshot.py ~/Downloads/Screenshot_20260727-143000.png
```

It lands in `store/screenshots/` under its original name — rename it to the
slot it replaces (e.g. `01-meter.png`), since Play orders screenshots by upload
order and the numbering here is the intended running order.

Do not skip the script and copy the file in by hand. Phones shoot taller than
Play allows: 1080×2400 is 2.22:1 against a 2:1 limit, so a raw screenshot is
rejected at upload. The script pads the short axis out to exactly 2:1 with the
app's own background colour rather than cropping, so no UI is lost and the
added margin is near-invisible on Third Stop's dark screens. It also downscales
anything over Play's 3840px ceiling and warns if a side falls under 320px.

## Listing copy

**App name** (30 max)

```
Third Stop: Film Light Meter
```

**Short description** (80 max)

```
A light meter for film cameras — reflective, incident and reciprocity.
```

**Full description** (4000 max)

```
Third Stop turns your phone into a handheld exposure meter for film
photography — built for cameras with no meter, or a meter you no longer trust.

REFLECTIVE METERING
Point the camera at your subject. Tap anywhere in the frame to spot-meter that
area, or average the whole scene. The reading comes from the phone's converged
auto-exposure rather than a guess at the raw pixels.

INCIDENT METERING
Use the ambient light sensor to meter the light falling on your subject, the
way a dome-equipped handheld meter does — hold the phone at the subject's
position, facing the camera.

ZOOM AND FOCAL LENGTH
Pinch to zoom and the viewfinder reports the 35mm-equivalent focal length, so
you can meter through roughly the same framing your lens sees. Pick a preset to
match the lens actually on your camera.

THE DIALS
Lock ISO and aperture to get the shutter speed, or lock the shutter to get the
aperture. Everything moves in third stops, with ±3 EV of exposure compensation.
Hold a reading to freeze it while you recompose.

RECIPROCITY FAILURE
Pick your stock and long exposures are corrected automatically. Both the
metered and the corrected time are shown, so you always know what the meter
actually said. Covers common Ilford, Kodak and Fujifilm emulsions, and carries
the manufacturer's own caveats where they give them.

READING LOG
Save any reading with a note, review it later, and tap to push it back onto the
dials. Swipe to delete.

CALIBRATION
Phone meters vary between devices by up to ±0.7 EV, and ambient light sensors
are often coarsely quantised. The calibration wizard matches this meter to a
meter you trust, to sunny 16, or to a camera you know — sampling at several
light levels for a better offset. Reflective and incident calibrate separately.

PRIVACY
No account. No ads. No analytics. No internet permission at all, so the app
cannot send anything anywhere. Camera frames are analysed on the device and are
never recorded. Your log and settings stay on your phone.

A NOTE ON ACCURACY
This is a working tool, not a novelty. But phone hardware varies, so calibrate
against a trusted reference before relying on it for critical work. Reciprocity
data is encoded from manufacturer datasheets as approximations — check the
current datasheet when it matters.
```

## Console answers

**Package name** — `com.kevinboutwell.thirdstop`. This is permanent once the
app is created in the Console and can never be changed, so confirm it before
creating the app.

**Privacy policy URL** — `https://kcb064.github.io/third-stop/`
(served from `docs/index.html`; enable Pages on the repo first — Settings →
Pages → deploy from the default branch, `/docs` folder). Renaming the repo
again would change this URL and Play would have to be updated to match.

**Data safety** — the honest answer to every collection question is *no*:

- Does your app collect or share any of the required user data types? **No**
- Is all of the user data collected by your app encrypted in transit? *n/a*
- Do you provide a way for users to request that their data is deleted? *n/a*

The app declares no `INTERNET` permission, bundles no analytics, ads or
third-party SDKs, and writes only to its own private storage. The `CAMERA`
permission is a *permission*, not data collection — Play asks about them
separately, and camera frames are never stored or transmitted.

**Other declarations**

- Ads: no
- In-app purchases: no
- App access: all functionality available without an account
- Content rating: answer the questionnaire truthfully; a metering utility with
  no user content, no ads and no data collection rates Everyone / PEGI 3
- Target audience: 18+ (or 13+); the app is not directed at children
- Government / financial / health app: no
- News app: no
