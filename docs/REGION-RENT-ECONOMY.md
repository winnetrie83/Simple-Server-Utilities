# Region Rent Economy

## Ownership model

Server regions are owned by the server. Players may be assigned as **managers** or **members**, but neither role receives economic ownership or rental income.

- Managers may administer ordinary region settings where permissions allow it.
- Members receive normal region access.
- Renters receive temporary member access for the active rental.
- Administrators act on behalf of the server and are not treated as owners.

Older region files may contain an `owners` array. SSU 1.4.0-dev2.1 reads those UUIDs as `managers` and writes only the new `managers` field on the next save.

## Rent and renewal payments

A successful rent or renewal performs these steps:

1. Prepare a durable region-rent journal record.
2. Debit the exact configured price from the renter in minor currency units.
3. Persist the new renter, term and refundable-value state.
4. Mark the linked journal operation complete.

The payment is a deliberate **money sink**. It is not credited to an administrator, region manager or permanent server account.

This is useful for economy balance: currency introduced through rewards or other systems can leave circulation through server services such as region rent.

## Cancellation refunds

Refund percentages remain configurable separately for:

- cancellation by the renter;
- cancellation by an administrator.

The eligible value is frozen before a reset job starts. Timed rentals receive a pro-rata refund based on the remaining eligible term. Paused rentals keep their frozen remaining time and refundable value.

A refund is recorded as a credit back to the former renter. It conceptually reverses part of the earlier money sink. The region-rent journal makes the operation idempotent: a failed or interrupted refund can be retried, but it cannot be paid twice.

Refunds do not depend on a treasury balance.

## Expiry

Natural expiry does not normally grant a cancellation refund. When configured, SSU first restores the saved region snapshot and only then removes the renter's access.

## Snapshot interaction

The existing region safety rules still apply:

- region bounds may not be changed while rented;
- destructive operations are blocked while a relevant job or unresolved journal operation exists;
- reset-on-expiry and reset-on-cancellation use the saved region snapshot;
- the rental's refundable value is frozen before reset work begins.

## Legacy dev2 treasury compatibility

The permanent treasury shipped only in the experimental 1.4.0-dev2 test source. Dev2.1 removes that account when the economy loads and retires any remaining balance instead of assigning it to a player.

Historical transaction and rent-journal fields remain readable so an existing dev2 test world can migrate without corrupting old records. New dev2.1 rental operations do not create treasury income or owner-payout transactions.

## Event funding later

Temporary community funding goals should be implemented as a separate event system rather than as a permanent server bank. Such a campaign can track contributions toward a specific unlock and remove the collected funds when the campaign completes or expires.
