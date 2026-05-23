package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceAdminRefundCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceAdminRefundUseCase;

public class AdminAuditedExecuteMarketplaceAdminRefundUseCase
    implements ExecuteMarketplaceAdminRefundUseCase {

  private final ExecuteMarketplaceAdminRefundUseCase delegate;

  public AdminAuditedExecuteMarketplaceAdminRefundUseCase(
      ExecuteMarketplaceAdminRefundUseCase delegate) {
    this.delegate = delegate;
  }

  @Override
  @AdminOnly(
      actionType = "MARKETPLACE_ADMIN_REFUND",
      targetType = AuditTargetType.MARKETPLACE_ESCROW_RESERVATION,
      operatorId = "#command.operatorId()",
      targetId = "'reservation:' + #command.reservationId()",
      detail = {
        "operatorUserId=#command.operatorId()",
        "reason=#command.reasonCode()",
        "memo=#command.memo()",
        "orderKey=#result == null ? null : #result.orderKey()"
      })
  public MarketplaceAdminExecutionResult execute(ExecuteMarketplaceAdminRefundCommand command) {
    return delegate.execute(command);
  }
}
