# Meter screen redesign — "Answer first + drag rulers" (option 2a)

Handoff for implementing the redesigned meter screen in `kcb064/Light-meter`
(branch `claude/android-light-meter-app-r8qi53`). Reference mock:
`meter-screen-2a.png` (412px-wide phone frame).

All colors/typography already exist in `ui/theme/Theme.kt` and `Type.kt` —
no theme changes needed except one added text style (see §7). Design-system
rules that still bind: dark-only, one amber accent (`primary = #FFB74D`),
tabular figures everywhere numeric, meter screen never scrolls, steps not
sliders.

## 1. Screen structure (top → bottom)

Replace the current `MeterScreen.kt` column (header row → TabRow →
panel → EvReadout → SolutionPanel → DialsRow → chips row → buttons) with:

1. **EV status chip** — centered pill, `surfaceContainer` bg, height 28dp,
   radius full. Content: 6dp amber dot + `EV 6.5 · live`
   (`labelLarge`, `onSurfaceVariant`). States:
   - live: amber dot, "· live"
   - held: dot + text go `primary`, "· held"
   - no reading: no dot, "EV — · waiting"
2. **Solution (the answer)** — centered, `FontWeight.Light`, **96–104sp**,
   letterSpacing −2sp, tnum, `onSurface`. Behind it a faint radial amber
   glow (radial gradient `primary` @ 13% alpha → transparent, ~260×120dp,
   purely decorative).
3. **Solution caption** — `SHUTTER · A PRIORITY` (or `APERTURE · S PRIORITY`),
   `labelMedium`, letterSpacing 2sp, color `primary`.
   Reciprocity note (when film set) goes directly under it in `primary`,
   `titleMedium`, unchanged logic from `SolutionPanel`.
4. **Viewfinder card** — the camera preview moves into a rounded card
   (radius 16dp, clipped), fills remaining vertical space (`weight(1f)`),
   margin 16dp horizontal. Overlaid on the glass:
   - top-left: Spot / Average chips (see §4)
   - top-right: "Incident" mode chip — replaces the TabRow entirely
   - bottom-center: hint text `tap to meter · long-press to hold`
     (`labelMedium`, 55% white)
   - spot reticle: existing `ReflectiveViewfinder` crosshair, unchanged
   Incident mode swaps the preview for the current lux readout inside the
   same card.
5. **Control deck** — one `surfaceContainer` (#17171A) card, radius 20dp,
   margin 16dp horizontal, containing three **drag rulers** (§2) separated
   by 1dp `outlineVariant` hairlines, then a chip row (§4) below a hairline.
6. **Action row** — 52dp buttons: circular outlined icon button (reading
   log, `Icons.AutoMirrored.Filled.List`), outlined `Hold` (amber outline +
   amber text; label flips to `Resume` when held), filled `Save`. Settings
   icon moves next to the log button or overflow — no more app bar.

## 2. Drag rulers (replaces `StepperDial`/`EcDial` arrows)

Each ruler is a 54dp-tall row inside the deck:
- Label left, fixed 78dp width: `ISO` / `APERTURE` / `EC`
  (`labelMedium`, letterSpacing 1sp, `onSurfaceVariant`).
- Value strip fills the rest: horizontally scrollable third-stop table,
  selected value snapped to center in `primary`, 24sp Medium tnum;
  neighbors at 15sp/14sp in dimmed grays (#84817a, #4a4a50).
- Edge fade: horizontal alpha mask (transparent → opaque 18% → 82% →
  transparent) — this is the swipe affordance.

Implementation sketch: `LazyRow` + `rememberSnapFlingBehavior`, or a
`Modifier.draggable` accumulating drag distance → index into `Stops.ISOS` /
`Stops.APERTURES` / `Stops.SHUTTERS` / EC thirds (−9..+9, formatted by the
existing `formatEcThirds`). The middle ruler shows APERTURE or SHUTTER
depending on priority, same as today's `DialsRow` logic.

**Haptics:** `LocalHapticFeedback` — `TextHandleMove`-style tick per third
stop, stronger (`LongPress`) detent at full stops. Value snaps on release.

## 3. Header removal

Delete the "Light Meter" title row and the Reflective/Incident `TabRow`.
Mode switching = the "Incident" chip on the viewfinder glass (tap toggles;
when in incident mode the chip reads "Reflective"). Disable it (38% alpha)
when `!state.hasLightSensor`, keeping the current behavior.

## 4. Chips

- **On-glass chips** (Spot/Average/Incident): 28dp pill; selected =
  `primaryContainer` @ 85% alpha bg + `primary` text; unselected =
  scrim bg (black 50%) + 1dp white-14% border + 80% white text. Blur behind
  (`Modifier.blur` on a scrim copy or just the translucent bg) is optional.
- **Deck chip row**: `A priority` / `S priority` / film chip, centered,
  30dp height, radius 8dp; selected = `primaryContainer` bg + `primary`
  text, unselected = 1dp `outline` border + `onSurfaceVariant` text.
  Film chip keeps opening the existing `ModalBottomSheet`.

## 5. Gestures (new)

- Long-press anywhere on the viewfinder → `toggleHold` (+ `LongPress`
  haptic). Keep the Hold button as the discoverable path.
- Tap-to-meter unchanged (`onMeterAt`).
- Long-press Save → save-with-note dialog; plain tap saves immediately
  with empty note (undo stays snackbar-based per design principles).

## 6. States to keep wired (logic unchanged, new placement)

- `isSettling` / `notConverged` / `apertureIsFallback` status strings →
  small `labelMedium` line under the EV chip.
- `outOfRange` "clamped" warning → `error` color, same spot.
- Permission / unsupported-camera messaging renders inside the viewfinder
  card, same copy as today.

## 7. Theme addition

`Type.kt`: bump `displayLarge` to 96sp (or add `displayXL = 104sp`,
Light, tnum, letterSpacing −2sp) for the solution. Everything else maps to
existing roles — no new colors.

## 8. Non-goals

Log, settings, calibration screens unchanged. Metering math, ViewModel,
data layer untouched — this is a `MeterScreen.kt` + `ExposureDials.kt`
(→ rename `DragRuler.kt`) rewrite only.
