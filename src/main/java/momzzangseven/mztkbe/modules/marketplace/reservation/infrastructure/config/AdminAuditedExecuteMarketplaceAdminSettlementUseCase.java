package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceAdminSettlementCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ExecuteMarketplaceAdminSettlementService;

public class AdminAuditedExecuteMarketplaceAdminSettlementUseCase
    implements ExecuteMarketplaceAdminSettlementUseCase {

  private final ExecuteMarketplaceAdminSettlementService delegate;

  public AdminAuditedExecuteMarketplaceAdminSettlementUseCase(
      ExecuteMarketplaceAdminSettlementService delegate) {
    this.delegate = delegate;
  }

  @Override
  @AdminOnly(
      actionType = "MARKETPLACE_ADMIN_SETTLE",
      targetType = AuditTargetType.MARKETPLACE_ESCROW_RESERVATION,
      operatorId = "#command.operatorId()",
      targetId = "'reservation:' + #command.reservationId()",
      detail = {
        "operatorUserId=#command.operatorId()",
        "reason=#command.reasonCode()",
        "memo=#command.memo()",
        "orderKey=#result == null ? null : #result.orderKey()"
      })
  public MarketplaceAdminExecutionResult execute(ExecuteMarketplaceAdminSettlementCommand command) {
    return delegate.execute(command);
  }
}
