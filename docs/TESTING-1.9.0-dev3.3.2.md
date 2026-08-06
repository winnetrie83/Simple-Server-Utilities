# SSU 1.9.0-dev3.3.2 test checklist

## Build and connection

- Build with Java 25 using `gradlew.bat clean build`.
- Install exactly dev3.3.2 on both client and server; protocol remains 88.
- Confirm an existing dev3.3.1 world loads without any schema migration prompt or data loss.

## Rank Prefix Editor

- Open Admin Center > Rank Management > Prefix for at least the `admin` and `default` ranks.
- Confirm all sixteen palette buttons have their actual colour as the button fill.
- Confirm every colour name remains readable, including White, Yellow, Lime, Light Blue and Black.
- Select only part of a prefix and apply multiple colours plus B/I/U/S.
- Save, reopen the editor and confirm text and formatting persist exactly.

## Rank Management layout

- Confirm the second-row rename field is approximately half the former width.
- Confirm the field hint and the line below explain that it supplies the new name for a row's Rename button.
- Confirm Title Manager and Refresh are fully clickable and do not overlap the rename field.
- Enter a new rank name, click Rename on a chosen rank and verify only that rank is renamed.
- Repeat at multiple GUI scales and window resolutions.

## Title Administration

- Open Title Manager from Rank Management.
- Confirm the Title display-name input is twice its former width and uses the whole editor column.
- Confirm Title ID, acquisition requirement, colour/unlock controls, preview, manual grant controls and Close remain non-overlapping and clickable.
- Create and edit a title with a long display name near the 48-character limit.
- Reopen Title Manager and confirm the title name persists.

## Chat formatting

- Configure a multi-colour prefix without a trailing space, for example `[Admin]`.
- Send chat as that player and confirm the exact visible shape is `[Admin] PlayerName: message`.
- Confirm the vanilla `<PlayerName>` wrapper is gone and the player name appears exactly once.
- Confirm the rank prefix keeps all configured colours and styles.
- Confirm the player name and chat text remain normal/unstyled.
- Repeat with the default rank, a prefix that already ends in a space, two simultaneous players and a dedicated server.
