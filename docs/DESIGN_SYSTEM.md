# PlantCare Design System (Mint Garden, 2026-10)

Read this before any UI work. Every screen should be built from the same
tokens, so the app looks like one product.

## 1. Colour — `res/values/colors.xml` + `res/values-night/colors.xml`

The `pc_*` roles follow Material 3. The brand green is generated from seed
**#2EB85F** (HCT tonal palette):

| Role | Light | Dark | Use |
|---|---|---|---|
| `pc_primary` | **#007B39** (tone 45) | **#5CDF81** (tone 80) | Buttons, progress, selection, links |
| `pc_primaryContainer` | #D4F2DC | #005324 | Soft highlights, Pro card |
| `pc_tertiaryContainer` | #F5D27A (amber) | #5A4312 | Warm accent: overdue badge, vacation |
| `pc_background` | #EAF1E2 | #101510 | Page background |
| `pc_surface` | #FFFFFF | #1C211C | **Cards**. In dark mode the card is lighter than the background |
| `pc_onSurface` | #1B3A2A | #E5EAE1 | Primary text |
| `pc_onSurfaceSecondary` | #5E6E5F | #BCCABA | Secondary text |

Rules:
- White text on `pc_primary` must be readable. #007B39 gives 5.4:1. The old
  #2EB85F gave 2.6:1, which is why buttons looked washed out.
- Light and dark use the **same hue**. The old dark theme used a different
  teal (#5DDABE).
- Do not hard-code hex values in layouts or Compose. Illustrations are the
  only exception.
- Overdue is amber (`tertiaryContainer`), not red. Red is only for errors and
  destructive actions.

## 2. Type
- XML: `TextAppearance.PlantCare.*`, with Inter (400/600) for UI text and
  Manrope ExtraBold for the brand headline.
- Compose: `PlantCareTypography` (M3) in `ui/theme/Type.kt` uses the same
  families. Its headline sizes are display/headline Manrope 22–34sp, and its
  title/body/label sizes are Inter 11–19sp.

## 3. Theme
- `Base.Theme.PlantCareApp` (`values/themes.xml`) is the single theme for light
  **and** dark. `values-night/themes.xml` only overrides the system-bar flags
  and the plain window background. Do not copy the whole theme into night again
  (the old copy had drifted and was missing the shape styles).
- Compose: wrap every screen in `PlantCareTheme { }`. It provides M3
  `MaterialTheme` and a legacy M2 theme with the same tokens for the older
  `weekbar/` screens. New code uses `androidx.compose.material3.*`.
- Spacing and radius come from `ui/theme/Dimens.kt` (`PlantCareSpacing`,
  `PlantCareRadius`). Gutter is 20dp, section gap 28dp, card radius 20dp.

## 4. Screens
- New screens are stateless Compose (`XScreen(state, callbacks)`), with sample
  data and `@Preview`s next to them. The reference is `ui/home/`.
- Settings rows: each row gets its own Material Symbols icon
  (`drawable/ic_set_*`). Do not reuse one generic icon.

## 5. Visual verification (required for UI changes)
1. Run `./gradlew recordRoborazziDevDebug`. It renders
   `*ScreenshotTest.kt` into `app/src/test/screenshots/`.
2. Look at the PNGs. A UI task is not done until someone has seen the result.
3. Run `./gradlew verifyRoborazziDevDebug`. It fails on unintended visual
   changes.

Current baselines: `screenshots/home/` (light, dark, empty, vacation, full page)
and `screenshots/settings/` (light, dark). Downscaled copies for GitHub are in
`docs/design/`.

## 6. Status
| Area | Status |
|---|---|
| Palette (contrast, dark-mode hue, dark cards) | ✅ app-wide |
| Unified light/dark theme | ✅ |
| Home tab (Compose) | ✅ redesigned |
| Settings icons | ✅ |
| Mein Garten, plant detail (dialog → full screen) | ⏳ next |
| Diagnose screen | ⏳ |
| Store screenshots | ⏳ after the above |
