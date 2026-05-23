package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

public record MarketplaceInternalExecutionPolicyStatus(
    boolean enabled,
    boolean marketplaceAdminSettleEnabled,
    boolean marketplaceAdminRefundEnabled) {}
