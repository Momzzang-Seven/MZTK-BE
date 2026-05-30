package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

public record MarketplaceWeb3InternalExecutionPolicyStatus(
    boolean enabled,
    boolean marketplaceAdminSettleEnabled,
    boolean marketplaceAdminRefundEnabled) {}
