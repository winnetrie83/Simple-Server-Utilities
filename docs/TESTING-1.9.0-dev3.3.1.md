# SSU 1.9.0-dev3.3.1 test checklist

This is a compile hotfix above dev3.3. All dev3.3 functional tests still apply.

## Build

- Run `gradlew.bat clean build` with Java 25.
- Confirm there are no unresolved-method errors in `DamageIndicatorEvents.java` or `PlayerIdentityManager.java`.
- Use exactly dev3.3.1 on both client and server; network protocol remains 88.

## Damage indicators

- Damage a player and several living mobs with armor and without armor.
- Confirm the displayed red amount matches actual health damage after armor, resistance and absorption handling.
- Heal a partly injured living entity and confirm a green indicator appears.
- Confirm no indicator appears for zero damage or zero effective healing.
- Test Floating, Hearts and Compact styles and the ON/OFF setting.
- Deny `ssu.damage_indicators.use` and confirm indicators are hidden for that viewer.

## Title administration

- Open Title Administration as a server operator without explicitly granting `ssu.permissions.admin`; access should work through the existing operator bypass.
- Deny or grant `ssu.permissions.admin` to normal players and confirm access follows the permission system.
- Create, edit, grant, revoke and delete a title.

## Regression

- Verify title selection in the normal Player Profile.
- Verify overhead title/rank visibility toggles.
- Verify styled rank prefixes above players and in chat.
- Verify the Tank Defensive Field still uses the vanilla lightning-impact sound and its original gameplay behavior.
