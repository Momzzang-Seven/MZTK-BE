package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceReservationStateException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceSchedulerAdminRefundCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceSchedulerAdminSettlementCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionPhase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminRefundReasonCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSettleReasonCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceSchedulerAdminRefundUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceSchedulerAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecuteMarketplaceSchedulerAdminExecutionServiceTest {

  @Mock private MarketplaceAdminExecutionOrchestrator orchestrator;

  @Test
  void schedulerRefundDelegatesWithoutAdminOnlyAuditBoundary() throws Exception {
    ExecuteMarketplaceSchedulerAdminRefundService service =
        new ExecuteMarketplaceSchedulerAdminRefundService(orchestrator);
    MarketplaceAdminExecutionResult execution = execution("MARKETPLACE_ADMIN_REFUND");
    given(
            orchestrator.executeSchedulerRefund(
                "run-1", MarketplaceAdminRefundReasonCode.TRAINER_TIMEOUT, 1L))
        .willReturn(execution);

    var result =
        service.execute(
            new ExecuteMarketplaceSchedulerAdminRefundCommand(
                1L, MarketplaceAdminRefundReasonCode.TRAINER_TIMEOUT, " run-1 "));

    assertThat(result.processed()).isTrue();
    assertThat(result.execution()).isSameAs(execution);
    then(orchestrator)
        .should()
        .executeSchedulerRefund("run-1", MarketplaceAdminRefundReasonCode.TRAINER_TIMEOUT, 1L);
    assertNoAdminOnlyAnnotation(ExecuteMarketplaceSchedulerAdminRefundUseCase.class);
    assertNoAdminOnlyAnnotation(ExecuteMarketplaceSchedulerAdminRefundService.class);
  }

  @Test
  void schedulerSettlementMapsLocalConflictToSkippedResult() {
    ExecuteMarketplaceSchedulerAdminSettlementService service =
        new ExecuteMarketplaceSchedulerAdminSettlementService(orchestrator);
    given(
            orchestrator.executeSchedulerSettlement(
                "run-2", MarketplaceAdminSettleReasonCode.BUYER_CONFIRMATION_TIMEOUT, 2L))
        .willThrow(
            new MarketplaceReservationStateException(
                ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS, "INVALID_LOCAL_STATUS"));

    var result =
        service.execute(
            new ExecuteMarketplaceSchedulerAdminSettlementCommand(
                2L, MarketplaceAdminSettleReasonCode.BUYER_CONFIRMATION_TIMEOUT, "run-2"));

    assertThat(result.skipped()).isTrue();
    assertThat(result.skipReason()).isEqualTo("INVALID_LOCAL_STATUS");
    assertNoAdminOnlyAnnotation(ExecuteMarketplaceSchedulerAdminSettlementUseCase.class);
    assertNoAdminOnlyAnnotation(ExecuteMarketplaceSchedulerAdminSettlementService.class);
  }

  private static void assertNoAdminOnlyAnnotation(Class<?> type) {
    for (Method method : type.getDeclaredMethods()) {
      assertThat(method.getAnnotation(AdminOnly.class)).isNull();
    }
  }

  private static MarketplaceAdminExecutionResult execution(String actionType) {
    return new MarketplaceAdminExecutionResult(
        1L,
        actionType,
        "0xorder",
        ReservationStatus.ADMIN_REFUND_PENDING,
        ReservationEscrowStatus.ADMIN_REFUND_PENDING,
        new MarketplaceAdminExecutionResult.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", LocalDateTime.of(2026, 5, 21, 12, 10)),
        new MarketplaceAdminExecutionResult.Execution("EIP1559", false, "SERVER_RELAYER_ONLY"),
        MarketplaceAdminExecutionPhase.QUEUED_FOR_SERVER_RELAYER,
        2000L,
        "/admin/web3/marketplace/reservations/1/refund-review",
        false);
  }
}
