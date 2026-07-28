package be.winnetrie.mod.simpleserverutilities.economy;

public enum EconomyTransactionType {
    TRANSFER,
    ADMIN_GIVE,
    ADMIN_TAKE,
    ADMIN_SET,
    STARTING_BALANCE,
    RECOVERY,

    REGION_RENT,
    REGION_RENEW,
    REGION_OWNER_PAYOUT,
    REGION_OWNER_PAYOUT_REVERSAL,
    REGION_REFUND,
    REGION_PAYMENT_ROLLBACK
}
