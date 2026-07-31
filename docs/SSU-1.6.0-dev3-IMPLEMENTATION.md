# SSU 1.6.0-dev3 implementation notes

## Direct Hologram Tool workflow

The Hologram Tool no longer requires a separate left-click anchor selection.

- A normal main-hand right-click opens a fresh editor.
- The server stores a placement point one world unit along the player's current eye/view vector.
- The client never submits placement coordinates; creation still uses the server-owned temporary anchor.
- Right-clicking a synchronized hologram with the named tool is intercepted on the logical client before the normal interaction pipeline. The client sends only the bounded hologram ID, and the server rechecks the named main-hand tool, module state and `ssu.holograms.admin` permission before returning full editor state. Remote Admin Center editing uses a separate trusted server action and does not require holding the tool.
- Targeting considers every rendered line and expands to the visible line width, including the link interaction suffix.

The editor now distinguishes create and edit mode. Edit mode preloads the complete stored definition, preserves its dimension, position and enabled state, supports a safe ID rename, and exposes a two-step delete action. Successful edit/delete operations return to and refresh the dashboard when it was the parent screen.

## Remote Admin Center management

The Admin Center includes a direct Holograms module and Admin Tools includes a Manage holograms shortcut. The server provides a searchable, paged list through the existing location-entry payload shape. Every action is typed and server-authoritative:

- **Edit** opens the same complete editor used for local interaction.
- **Teleport** resolves the target dimension and uses `TeleportSafety.findSafeDestination` near the hologram. The destination chunk is loaded by the existing resolver and the action fails rather than placing the administrator in a wall, fluid or unsupported position.
- **Delete** requires a second confirmation in the dashboard before the delete action is sent.

The local editor also has its own independent two-step deletion confirmation.

## Required utility-mining tools

Treecapitator accepts only a non-empty main-hand stack in `ItemTags.AXES`. Veinminer accepts only a non-empty main-hand stack in `ItemTags.PICKAXES`. This naturally includes correctly tagged modded tools.

The check runs during target resolution and again during asynchronous chain execution. Each extra block and each automatically removed natural leaf is guarded, so changing the held item or exhausting the tool stops further processing.

## Compatibility

- Version: `1.6.0-dev3`
- Network protocol: `25`
- Player UI preference schema: unchanged at `3`
- Hologram storage schema: unchanged at `1`
- Utility-mining placement tracking: unchanged
- Existing world data remains compatible
- Client and server must run the exact same dev3 build because hologram editor payloads changed
