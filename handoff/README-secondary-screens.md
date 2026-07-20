# Secondary screens — same design language as the meter redesign

Companion to `README.md` (meter screen 2a). Reference mocks:
`log-3a.png`, `settings-3b.png`, `calibration-3c.png`.

## Shared rules (all screens)

- **No `TopAppBar`.** Header = 44dp back chevron IconButton + letterspaced
  caps title (`labelMedium`, letterSpacing 2.5sp, `onSurfaceVariant`),
  optional trailing meta text.
- **Deck cards**: content groups live in `surfaceContainer` (#17171A)
  cards, radius 20dp, 16dp horizontal margin, 1dp `outlineVariant`
  hairlines between rows inside a card.
- **Tag chips** (read-only metadata): 22dp height, radius 6dp,
  `#232328` bg, `onSurfaceVariant` text, `labelMedium` 11sp. Amber variant
  (`primaryContainer` bg + `primary` text) only for reciprocity-corrected
  values.
- **Drag ruler** (from `DragRuler.kt`): reuse wherever a value steps —
  never `Slider`, never stepper arrows.

## 1. LogScreen.kt

- Group readings by day: `TODAY` / `MON JUL 14` section labels
  (`labelMedium`, letterSpacing 1.5sp) above one deck card per day.
- Row layout: exposure triple `f/5.6 · 1/10 · 400` at 24sp Regular tnum
  (row anchor) + right-aligned time (`bodySmall`); below it a tag-chip row:
  EV, mode, EC (if ≠0), film + corrected shutter (amber chip). Note renders
  italic `bodySmall` under the chips.
- **Delete = swipe left** (`SwipeToDismissBox`): reveal `error`-tinted
  trash on an error-alpha gradient; keep the existing undo snackbar.
  Remove the per-row delete IconButton.
- **New: tap a row → re-apply reading to the meter** (restore ISO / EC /
  film / priority in the ViewModel, navigate back, `LongPress` haptic).
- Header meta: reading count ("14 readings").

## 2. SettingsScreen.kt

- Intro copy stays, above the cards.
- One deck card per mode (Reflective / Incident):
  - Title row: mode name (`titleMedium`) + subtitle ("camera" /
    "light sensor") + **big offset value right-aligned**, 22sp Medium tnum —
    `primary` when non-zero, `onSurfaceVariant` when 0.0.
  - **Offset control = DragRuler**, range −2.0..+2.0 EV in 0.1 steps,
    replacing the `Slider`. Haptic tick per 0.1, firm detent at 0.0.
  - Footer row: provenance text ("Calibrated Jul 12 via reference meter" /
    "Not calibrated yet") + outlined pill "Wizard" button (36dp).
- About section becomes its own quiet card: `ABOUT` letterspaced label +
  `bodyMedium` copy.

## 3. CalibrationScreen.kt

- Three deck cards, one per step, each headed by a 24dp amber-container
  circle with the step number + letterspaced caps label:
  `1 REFERENCE`, `2 THIS PHONE`, `3 SAMPLES`. Screen still scrolls.
- Step 1: source FilterChips restyle to deck chips (32dp, radius 8dp,
  selected = `primaryContainer`+`primary`); EV/Camera entry toggle right-
  aligned in the same row. Camera-settings path replaces the three
  `StepperDial`s with **DragRulers** (ISO / aperture / shutter); computed
  "= EV 14.6 at ISO 100" line below.
- Step 2: live indicator dot ("live") in the card header; viewfinder in a
  12dp-radius clipped box; live EV at `displaySmall` Light with **Δ vs
  trusted reading** beside it in `primary` (`titleMedium`) whenever both
  exist.
- Step 3: "Add sample" = outlined amber pill in the header (disabled 38%
  until both readings exist); samples render as `#232328` chips (+0.5 …).
  Below a hairline: offset verdict (20sp `primary` + explanation
  `bodySmall`) with filled **Apply** pill (48dp) right-aligned.
  Spread / large-offset warnings keep `error` color under the verdict.

## Non-goals

ViewModels, DAO, calibration math untouched. Film picker sheet not
covered here (ask if wanted).
