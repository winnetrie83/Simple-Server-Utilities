# SSU module architecture freeze — 1.9.0-dev3.41.3

## Purpose
This build freezes the Minecraft 26.2 module architecture before the 1.21.1 backport. New gameplay features and persistence-format changes should wait until the backport reaches feature parity.

## Frozen lifecycle rules
- Core infrastructure is always available: `storage`, `transactions`, `jobs`, `performance`, `content_core`, `ui_preferences`, and `menu`.
- Feature modules expose a configured state and an effective state.
- A configured feature becomes `BLOCKED` only when a **required** dependency is unavailable. Its configured preference is retained and it restarts automatically when the dependency returns.
- **Optional** and **integration** dependencies never block activation. Call sites must degrade safely when those modules are absent.
- Required dependencies define deterministic start order and reverse stop order.
- Disabling or blocking a module never deletes its persisted data.

## Boundary hardening in dev3.41.3
- Registered feature events fail closed when their effective module is inactive.
- Protection events are shared by Claims and Regions and therefore run only if at least one of those modules is active.
- Serverbound feature GUI/network handlers reject stale requests after a module has been disabled or blocked.
- Legacy feature commands reject execution while the feature is inactive. NPC Shop-specific commands additionally require `npc_shops`.
- The player Dashboard hides inactive feature tiles; administration/module settings continue to show OFF/BLOCKED modules for recovery.

## Economy boundary
`EconomyService` is the portable transaction contract and `EconomyProvider` identifies the active provider. Shared Content Core money rewards now use `EconomyServices.activeService()`.

Consumers that require SSU-specific journal recovery, typed internal transaction records, account lookup, or `EconomySettings` remain intentionally bound to `EconomyManager`. A future provider must either extend the provider contract with equivalent semantics or those features must remain unavailable for that provider. `ssu_digital` remains the only provider in this freeze build.

## Backport rule
Create the 1.21.1 branch from this architecture state and port platform/API differences without redesigning module semantics in parallel. If a module relationship must change, apply the same semantic change deliberately to both branches rather than allowing them to drift silently.

## Compatibility
- Minecraft 26.2 architecture-freeze candidate: `1.9.0-dev3.41.3`.
- Network protocol: `119`.
- Persistence schemas: unchanged from dev3.41.2.
