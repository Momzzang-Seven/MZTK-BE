package momzzangseven.mztkbe.modules.web3.execution.application.dto;

public record InternalExecutionIssuerPolicyView(
    boolean enabled,
    boolean qnaAdminSettleEnabled,
    boolean qnaAdminRefundEnabled,
    boolean marketplaceAdminSettleEnabled,
    boolean marketplaceAdminRefundEnabled) {}
