package momzzangseven.mztkbe.modules.web3.wallet.domain.model;

/**
 * Wallet event type enum
 */
public enum WalletEventType {

    /** both fresh and re-registered */
    REGISTERED,

    /** User unlink the wallet by himself */
    UNLINKED,

    /** Hard deleted by scheduler */
    HARD_DELETED,

    /** Deactivated due to user withdrawal */
    USER_DELETED,

    /** Blocked by admin */
    BLOCKED
}
