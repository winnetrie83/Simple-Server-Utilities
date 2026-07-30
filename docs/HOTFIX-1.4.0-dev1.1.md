# SSU 1.4.0-dev1.1 hotfix

Minecraft 26.2's `ServerPlayer` mapping does not expose `getServer()` directly.
`SsuMenuService` accidentally used that method in ten dashboard code paths.

All calls now use the mapping already used elsewhere in SSU:

```java
player.level().getServer()
```

and, for actor parameters:

```java
actor.level().getServer()
```

Affected paths:

- claim trusted-player display names;
- permission-player lookup;
- economy account lookup for payments;
- rental cancellation;
- home and warp dimension lookup;
- permission rank/set/unset actions;
- region owner/member display names.

Protocol remains 13. No save format or gameplay logic changed.
