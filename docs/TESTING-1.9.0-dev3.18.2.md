# SSU 1.9.0-dev3.18.2 runtime checklist

Use the same `1.9.0-dev3.18.2` build on client and dedicated server.

## Compile
- Run the normal local Gradle build.
- Confirm SsuDashboardScreen.IntSettingSlider compiles without an AbstractSliderButton abstract-method error.
- Confirm EntityInsightService compiles without TamableAnimal or getTags/entityTags errors.

## Entity Insight regression
- Open Player Settings > Combat and move both Entity Insight sliders.
- Confirm slider values update and persist.
- Confirm tamed animals are friendly/green when not targeting a player.
- Confirm SSU NPCs do not receive duplicate Entity Insight labels.

## Dashboard regression
- Confirm Support, Kits and Mines still use the supplied dedicated icons and open their original pages.
