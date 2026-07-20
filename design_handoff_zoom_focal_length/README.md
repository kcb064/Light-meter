# Handoff: Zoom / 35mm-Equivalent Focal Length

## Overview
Adds a zoom feature to the Light Meter app's reflective meter screen. The user zooms the phone camera (lens pills, pinch, or a lens-preset bottom sheet) and the app reports the framing as a 35mm-equivalent focal length. Purpose: **assist spot metering by framing the phone to match the lens on the user's real camera** (e.g. frame like their 50mm, then spot-meter what that lens will see).

## About the Design Files
`Meter Screen — Zoom v2.dc.html` is a **design reference created in HTML** — a prototype showing intended look and behavior, not production code. The design was built against the real codebase at `kcb064/Light-meter` (branch `claude/android-light-meter-app-r8qi53`) and reuses its existing components. Implement by **modifying the existing Kotlin files** — do not restructure the screen.

## Fidelity
**High-fidelity.** The prototype recreates the existing `MeterScreen.kt` layout exactly (EV status chip → status line → solution block → viewfinder card → control deck → action row) and adds only the zoom feature. Colors/type are the existing `Theme.kt` / `Type.kt` values.

## Files to touch
- `ui/meter/MeterScreen.kt` — zoom pills + zoom status line in `ViewfinderCard`; "Lens…" `DeckChip` in `ControlDeck`; lens bottom sheet (clone the film-sheet pattern in `MeterScreen`).
- `ui/meter/ReflectiveViewfinder.kt` — pinch gesture (`detectTransformGestures` alongside the existing `detectTapGestures`).
- `ui/meter/MeterViewModel.kt` — zoom state (`currentMm`, `lensPresetMm`), lens enumeration, `setZoomMm()`, `setLensPreset()`; persist like dial state in `SettingsRepository`.
- `camera/CameraMeter.kt` — expose available lenses + apply `zoomRatio` via `cameraControl.setZoomRatio()`; enumerate physical lenses (`CameraCharacteristics.getPhysicalCameraIds` + per-lens `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` / `SENSOR_INFO_PHYSICAL_SIZE` → 35mm-equivalent mm), falling back to `zoomRatioRange` if physical IDs aren't exposed.

## New UI — all inside the existing screen

### 1. Zoom pills (bottom of `ViewfinderCard`, above the hint text, centered)
- One pill per device-reported physical lens. Prototype set: 12 (ultra-wide), 24 (wide), 120 (telephoto); baseline main-lens mm is device-specific.
- Reuse **`GlassChip`** exactly (28dp, CircleShape): unselected = `Color.Black.copy(alpha=.5f)` bg + 1dp `Color.White.copy(alpha=.14f)` border + `Color.White.copy(alpha=.8f)` labelLarge text, label = lens mm number ("12", "24", "120").
- Active pill (largest lens mm ≤ current mm) = selected GlassChip style (`primaryContainer.copy(alpha=.85f)` bg, primary text) showing **live rounded mm + factor**: "72mm · 3×". Factor: 1 decimal below 10×, integer above, strip ".0"; sub-1× shows "0.5×".
- Tap pill → zoom exactly to that lens's mm.
- Pills row sits 38dp from card bottom; the existing hint gains "· pinch to zoom": `tap to meter · long-press to hold · pinch to zoom`.

### 2. Zoom status line (12sp labelMedium, centered, 74dp from card bottom — fixed slot, never shifts)
- No preset: `≈ {mm}mm full frame · {lens name}` in onSurfaceVariant, appending ` + digital crop` when current mm exceeds the active lens's native mm by >0.5.
- Preset set: amber (primary), by delta d = mm − preset:
  - d == 0 → `matching your {t}mm lens`
  - |d| ≤ max(3, t×0.08) → `≈ your {t}mm lens (slightly tighter|slightly wider)`
  - else → `your {t}mm lens is (wider|tighter) than this frame`

### 3. Pinch to zoom (in `ReflectiveViewfinder`)
- `mm = startMm × zoomChange`, clamped to [smallest lens mm, largest lens mm × 3] (3× digital headroom). Continuous, not stepped. Apply with `setZoomRatio(mm / mainLensMm)`.
- Spot metering keeps using `previewView.meteringPointFactory` — it accounts for the zoomed crop automatically; zoom narrows the effective spot angle, which is the point.

### 4. "Lens…" chip (in `ControlDeck` chip row, after Film…)
- Reuse **`DeckChip`**: unset label "Lens…" (unselected style), set label "{mm}mm" (selected style — short label so the 4-chip row never overflows).
- Opens a **`ModalBottomSheet`** cloned from the film sheet: drag handle, section label "MATCH YOUR CAMERA'S LENS" (labelMedium, 1sp tracking, onSurfaceVariant, 24dp side padding), then 48dp `TextButton` rows: **Off, 24mm, 28mm, 35mm (wide), 50mm (normal), 85mm (portrait), 105mm, 135mm** — bodyLarge, selected row primary, dim bodyMedium note trailing.
- Selecting zooms exactly to the value (clamped) and dismisses.

## State & persistence
- `currentMm: Float` (default = main lens mm), `lensPresetMm: Int?` (null = off).
- Persist both in `DialState` / `SettingsRepository` like the other dials.
- Derived: active lens, zoom factor, digital-crop flag, status-line text.
- Reset zoom is NOT needed on mode switch, but incident mode hides all zoom UI (camera-only feature).

## Unchanged (for reference — prototype reproduces these)
- EV status chip: 28dp Surface pill, surfaceContainer, amber 6dp dot, "EV 11.3 · held" labelLarge tnum, primary when held.
- Solution block: displayLarge (96sp Light, −2sp) value, "SHUTTER · A PRIORITY" labelMedium primary 2sp tracking, amber radial glow 260×120dp @ 13% behind.
- Control deck: Surface 20dp radius surfaceContainer, three 54dp `DragRuler`s (ISO / APERTURE-or-SHUTTER / EC) with outlineVariant dividers; centered value headlineMedium primary, neighbors 15sp `#84817A` / 14sp `#4A4A50`, 72dp items, faded edges.
- Action row: 52dp OutlinedIconButtons (log, settings), Hold outlined pill (primary border), Save filled primary pill.

## Design tokens (existing Theme.kt — do not redefine)
primary `#FFB74D`, primaryContainer `#3A2B10`, onPrimary `#201400`, background `#0E0E10`, surfaceContainer `#17171A`, surfaceContainerHigh `#202024`, onSurface `#EDEAE4`, onSurfaceVariant `#9C988F`, outline `#3A3A40`, outlineVariant `#2A2A2E`. Tabular figures (`tnum`) on all numerics.

## Screenshots
- `screenshots/01-default-1x.png` — default, wide lens 1×
- `screenshots/02-telephoto-120mm.png` — telephoto pill active
- `screenshots/03-lens-sheet-open.png` — lens bottom sheet
- `screenshots/04-50mm-lens-matched.png` — 50mm preset matched

## Prototype
`Meter Screen — Zoom v2.dc.html` — open in a browser; scroll-wheel over the viewfinder simulates pinch; pills, priority chips, rulers, and the lens sheet are interactive.
