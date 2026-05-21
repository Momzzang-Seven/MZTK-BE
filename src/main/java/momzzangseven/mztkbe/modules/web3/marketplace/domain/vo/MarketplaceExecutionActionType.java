package momzzangseven.mztkbe.modules.web3.marketplace.domain.vo;

/** Marketplace execution actions that map by name to shared execution action codes. */
public enum MarketplaceExecutionActionType {
  MARKETPLACE_CLASS_PURCHASE,
  MARKETPLACE_CLASS_CANCEL,
  MARKETPLACE_CLASS_CONFIRM,
  MARKETPLACE_CLASS_EXPIRED_REFUND,
  MARKETPLACE_ADMIN_REFUND,
  MARKETPLACE_ADMIN_SETTLE;

  public boolean isUserAction() {
    return switch (this) {
      case MARKETPLACE_CLASS_PURCHASE,
              MARKETPLACE_CLASS_CANCEL,
              MARKETPLACE_CLASS_CONFIRM,
              MARKETPLACE_CLASS_EXPIRED_REFUND ->
          true;
      case MARKETPLACE_ADMIN_REFUND, MARKETPLACE_ADMIN_SETTLE -> false;
    };
  }

  public boolean isAdminAction() {
    return !isUserAction();
  }
}
