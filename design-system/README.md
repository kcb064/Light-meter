# Light Meter Design System

Design system for the Light Meter Android app (Jetpack Compose, Material 3).
Extracted from and kept in sync with the app theme at
`app/src/main/java/com/kevinboutwell/lightmeter/ui/theme/`.

## Principles

1. **Dark-only, by design.** The app is used next to cameras in low light.
   Surfaces are near-black (`#0E0E10` → `#202024`); there is no light theme
   and no dynamic color.
2. **One amber accent.** `#FFB74D` is the only saturated color — it marks
   what is *active or held*: held readings, selected chips, live stepper
   arrows, reciprocity notes, the spot reticle. Amber is kind to
   dark-adapted eyes.
3. **Numbers never jitter.** Every numeric style uses tabular figures
   (`tnum`). Live readouts update continuously; the layout must not breathe.
4. **The answer is the biggest thing on screen.** The solved exposure
   variable renders at display sizes in light weights — instrument markings,
   not headlines. Hierarchy: solution → EV → dials → microcopy.
5. **Fits on one screen.** The meter screen never scrolls: the viewfinder
   flexes, everything else keeps fixed compact heights.
6. **Steps, not sliders, for exposure.** ISO, aperture, shutter, and EC move
   through third-stop tables by index. Free input appears only in
   calibration.
7. **Undo over confirm.** Destructive actions (delete reading) execute
   immediately with a snackbar Undo.

## Structure

- `tokens/tokens.css` — canonical design tokens (colors, type, spacing, shape)
- `foundations/` — colors, typography, spacing & shape
- `components/` — buttons, filter chips, stepper dials, inputs, app bar & tabs,
  lists, overlays
- `patterns/` — readouts (EV + solution panel), viewfinder

Each preview HTML is self-contained and renders on the near-black background
it ships on.
