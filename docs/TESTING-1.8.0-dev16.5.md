# SSU 1.8.0-dev16.5 test checklist

## Internal economy accounts

1. Start a world that already contains **SSU Mail Escrow** and **SSU Auction House Tax** accounts.
2. Open Claim Settings → Trusted players → Manage. Neither internal account may appear.
3. Verify neither account appears in player permission/profile pickers or can be used as a player payment/mail recipient.
4. If either account was trusted in a claim in dev16.4, opening its Trusted players manager must remove it safely.
5. Verify Auction House and Mail money transfers still work and the internal balances/history remain intact.

## Block Information entities

1. Inspect a sheep: Health is shown; Armor and Toughness are absent.
2. Inspect an armored skeleton or other equipped mob: positive Armor is shown.
3. Inspect a mob with positive armor toughness: Toughness is shown.
4. Damage/heal a mob and verify the health line updates.
5. Verify non-living entities such as item frames do not show health/armor fields.
6. Repeat with Block Information debug mode enabled.
