# Permission editor — SSU 1.4.0-dev1.2

## Goal

The dashboard permission page is now a target-first editor instead of a free-text inspection form.

## Workflow

1. Choose **Players** or **Ranks** in the first dropdown.
2. Optionally enter a target filter and press **Filter**.
3. Open the second dropdown and choose a player or rank.
4. Optionally filter the permission keys.
5. Edit the paged permission list.

For a selected player, the page also offers a rank dropdown and an **Assign** button.

## Permission controls

- Boolean keys use an ON/OFF button.
- Integer keys use a whole-number field with server-enforced minimum and maximum values.
- Custom or externally registered keys use a text field.
- The `×` button removes the direct player/rank override and exposes the inherited or module-default result again.

Each row shows the direct/effective source. Hovering the row displays:

- the permission description;
- accepted value type and numeric range where relevant;
- current direct override or inherited/default value.

## Data and security

The client only requests bounded target lists and one bounded permission page. Every mutation is a closed typed action. The server resolves the target again, verifies `ssu.permissions.admin`, validates the key value and then updates the existing permission store.

The permission save format is unchanged. Known offline players are sourced from existing permission player profiles; players that have never joined and have no profile are not inventable from the client.

## Network compatibility

The dedicated editor payloads increase the network protocol from 13 to 14. Client and server must both use SSU 1.4.0-dev1.2.
