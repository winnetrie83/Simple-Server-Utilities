# SSU 1.5.0-dev1.1 compile hotfix

## Fixed

`MailComposeScreen` used the historical three-argument `AbstractContainerScreen` constructor and then assigned `imageWidth` and `imageHeight` afterward.

Minecraft 26.1+ makes these inherited fields final. Custom dimensions must be supplied to the five-argument superclass constructor instead:

```java
super(menu, inventory, title, 176, 248);
```

The title and inventory label offsets remain configurable and are still set after the superclass constructor.

## Compatibility

- Network protocol remains 20.
- No packet, menu, permission, mailbox or storage schema changed.
- Existing 1.5.0-dev1 data remains compatible.
- Client and server should still use the exact same built JAR.
