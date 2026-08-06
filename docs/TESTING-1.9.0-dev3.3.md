# SSU 1.9.0-dev3.3 test checklist

Base: `1.9.0-dev3.2.1`

This build introduces a global title catalogue, styled rank prefixes, player-controlled overhead identity visibility, Tank sound polish and damage/healing indicators. Client and server must use the exact same dev3.3 build because the network protocol changed to `88`.

## 1. Local build and startup

Build with Java 25:

```bat
gradlew.bat clean build
```

Then start both a dedicated NeoForge server and a separate client.

Confirm:

- no unresolved methods or payload registration errors;
- no duplicate payload/type registration errors;
- the server creates `identity/titles.json` on first startup;
- a joining player receives an `identity/players/<uuid>.json` record;
- dev3.2.1 minigames, permissions, mail, holograms and settings still load normally.

## 2. Tank Defensive Field sound

Start a team minigame with the Tank role and activate Defensive Field.

Confirm:

- the ability still applies its existing enemy-only radial knockback and Slowness;
- one vanilla lightning-impact sound plays at the Tank;
- the sound is audible to the relevant match players;
- no actual lightning bolt is spawned;
- no fire, lightning damage or block damage occurs;
- repeated activation respects the existing ability cooldown.

## 3. Global title migration

Use a player that already had a title selected in the Minigame Profile on dev3.2.1.

Confirm after first dev3.3 login:

- the former selection is migrated into the global identity record;
- the title is selected from `SSU > Profile`, not from Minigame Profile;
- Minigame Profile still shows XP, levels, badges, victory effects and challenges;
- Minigame Profile clearly redirects title selection to the normal Profile;
- the original title unlocks still follow minigame level requirements.

Default migrated titles:

- Rookie — minigame level 1
- Contender — minigame level 5
- Veteran — minigame level 10
- Champion — minigame level 20
- Elite — minigame level 30
- Legend — minigame level 40

## 4. Player title selection and visibility

Open `SSU > Profile > Choose title`.

Confirm:

- unlocked titles can be selected;
- locked or disabled titles cannot be selected;
- the chosen title survives relog and server restart;
- the selected title appears as one complete line above the player name;
- the complete title uses exactly one configured colour;
- `Settings > Identity > Title above name` immediately hides/shows the player's title for other clients;
- disabling the title does not remove the unlock or selected-title data;
- title rendering is correct at different distances, GUI scales, F1/HUD states and in first/third person.

## 5. Administrator title catalogue

Open the Title Manager as an administrator.

Create and test titles using every acquisition type:

- Free
- Minigame level
- Minigame wins
- Rank
- Permission
- Manual administrator grant

Confirm:

- title IDs and display names are validated and bounded;
- all 16 fixed colours are available in this exact order:
  White, Light Gray, Gray, Black, Brown, Red, Orange, Yellow, Lime, Green, Cyan, Light Blue, Blue, Purple, Magenta, Pink;
- editing an existing title preserves its enabled/disabled state;
- enabling and disabling a title updates online clients;
- deleting any title removes it from selection and manual unlocks;
- manual Grant and Revoke work for an online player;
- acquisition descriptions match the configured rule;
- catalogue changes survive restart;
- an intentionally empty custom catalogue is not silently repopulated when loaded.

Also verify that a non-admin cannot open or mutate the administrative catalogue.

## 6. Rank prefix editor

Open Admin Center/Rank Management and use the new `Prefix` action for several ranks.

Confirm:

- plain and multi-colour prefixes can be saved;
- selected text can independently receive Bold, Italic, Underline and Strikethrough;
- all 16 fixed colours are available;
- formatting control codes never appear visibly in the editor;
- the prefix survives server restart and normal rank editing;
- an empty prefix falls back safely to the existing bracketed rank name;
- long/newline-containing values are safely bounded to a single prefix line.

Test inherited and directly assigned ranks and confirm that SSU consistently uses the player's primary rank for display.

## 7. Rank prefix above player names

With two clients online, confirm:

- the styled rank prefix appears before the normal player name;
- only the prefix receives its custom colours/styles;
- the player name remains standard/unformatted;
- `Settings > Identity > Rank above name` immediately hides/shows that player's overhead prefix;
- hiding the overhead prefix does not change rank permissions or assignment;
- title and rank can independently be shown or hidden;
- rank plus player name does not overlap the separate title line.

## 8. Rank prefix in chat

Send chat messages from players with several differently formatted ranks.

Confirm:

- the primary-rank prefix appears before the player name;
- prefix colours and B/I/U/S formatting are retained;
- the player name and message remain standard/unformatted;
- disabling the overhead rank display does not remove the authoritative chat prefix;
- Unicode, punctuation and normal chat text remain intact;
- no prefix is duplicated by repeated login/sync events.

Also test compatibility with any other installed chat-formatting mod before treating this path as stable.

## 9. Damage indicators — basic behaviour

In `Settings > Combat`, enable Damage indicators and select each style:

1. Floating
2. Hearts
3. Compact

For each style, test damage and healing on:

- another player;
- hostile and passive mobs;
- an armored target;
- a target with absorption;
- a target already at or near full health;
- lethal damage;
- rapid repeated damage/healing.

Confirm:

- actual post-reduction health damage is displayed;
- damage numbers are always red;
- healing numbers are always green;
- overhealing only shows the amount that can actually restore health;
- zero/fully blocked damage does not create a number;
- indicators originate around the affected entity rather than the attacker/healer;
- numbers rise/fade and expire without accumulating permanently;
- Floating, Hearts and Compact are visually distinct;
- the selected style and enablement survive relog/restart.

## 10. Damage-indicator visibility and permission

Confirm:

- indicators are only sent to viewers within the bounded 64-block range;
- a viewer with the feature disabled receives no indicators;
- `ssu.damage_indicators.use` defaults to `true` when unset;
- setting the permission to `false` prevents the player from receiving indicators;
- attempting to enable the setting without permission fails safely;
- re-enabling the permission restores normal behaviour;
- one player's setting does not change another player's indicators.

Test with several nearby players to check for duplicate numbers and acceptable network/render performance.

## 11. Persistence and migration

Restart the server after changing:

- selected title;
- title visibility;
- rank visibility;
- damage-indicator enablement/style;
- title catalogue entries;
- rank prefixes;
- manual title grants.

Confirm all values return correctly. Inspect the JSON files and verify:

- Player UI preference schema is `11`;
- title catalogue schema is `1`;
- player identity schema is `1`;
- old UI-preference data migrates without losing existing minimap, map, mail, border or utility-mining settings;
- malformed identity/title files are archived safely rather than crashing startup.

## 12. Regression tests

Confirm that dev3.2.1 behaviour remains intact:

- preparation-only minigame startup;
- final 10-second countdown and `GO!`;
- in-match `U` overview and Leave match action;
- minigame progression, weekly challenges, badges and victory effects;
- all four built-in minigame runtime APIs restored in dev3.2.1;
- permission/rank assignment and inheritance;
- Mail and Floating Text rich-text editing;
- Treecapitator and Veinminer fixed 16-colour outline selection.
