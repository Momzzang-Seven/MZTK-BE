package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceSchedulerAdminSettlementCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSchedulerExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSettleReasonCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleSkipCategory;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ScheduleMarketplaceWeb3AutoSettleCandidateCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ScheduleMarketplaceWeb3AutoSettleResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceSchedulerAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ScheduleMarketplaceWeb3AutoSettleCandidateUseCase;

public class ScheduleMarketplaceWeb3AutoSettleCandidateService
    implements ScheduleMarketplaceWeb3AutoSettleCandidateUseCase {

  private final ExecuteMarketplaceSchedulerAdminSettlementUseCase executeUseCase;

  public ScheduleMarketplaceWeb3AutoSettleCandidateService(
      ExecuteMarketplaceSchedulerAdminSettlementUseCase executeUseCase) {
    this.executeUseCase = executeUseCase;
  }

  @Override
  public ScheduleMarketplaceWeb3AutoSettleResult execute(
      ScheduleMarketplaceWeb3AutoSettleCandidateCommand command) {
    command.validate();
    MarketplaceAdminSchedulerExecutionResult result =
        executeUseCase.execute(
            new ExecuteMarketplaceSchedulerAdminSettlementCommand(
                command.candidate().reservationId(),
                MarketplaceAdminSettleReasonCode.BUYER_CONFIRMATION_TIMEOUT,
                command.schedulerRunId()));
    if (result.processed()) {
      return ScheduleMarketplaceWeb3AutoSettleResult.scheduled();
    }
    return ScheduleMarketplaceWeb3AutoSettleResult.skipped(
        classify(result.skipCode()), result.skipReason());
  }

  private MarketplaceWeb3AutoSettleSkipCategory classify(String skipCode) {
    if (skipCode == null || skipCode.isBlank()) {
      return MarketplaceWeb3AutoSettleSkipCategory.UNKNOWN_STATE_SKIP;
    }
    return switch (skipCode) {
      case "NON_RETRYABLE_PREPARATION_FAILED" ->
          MarketplaceWeb3AutoSettleSkipCategory.NON_RETRYABLE_PREPARATION_FAILED;
      case "UNRESOLVED_MARKETPLACE_EXECUTION_EXISTS" ->
          MarketplaceWeb3AutoSettleSkipCategory.UNRESOLVED_EXECUTION_GUARD;
      case "CHAIN_ORDER_ABSENT",
              "CHAIN_ORDER_NOT_CREATED",
              "CHAIN_ORDER_ALREADY_REFUNDED",
              "CHAIN_ORDER_ALREADY_SETTLED",
              "CHAIN_MISMATCH_REQUIRES_SYNC",
              "MANUAL_SYNC_REQUIRED",
              "CONTRACT_DEADLINE_EXPIRED",
              "CLASS_NOT_ENDED" ->
          MarketplaceWeb3AutoSettleSkipCategory.DEADLINE_OR_CHAIN_PRECONDITION;
      case "ACTIVE_EXECUTION_EXISTS",
              "INVALID_LOCAL_STATUS",
              "ESCROW_NOT_LOCKED",
              "RESERVATION_NOT_USER_EIP7702",
              "PREPARED_SNAPSHOT_MISMATCH",
              "INTENT_BIND_CONFLICT",
              "IDEMPOTENCY_CONFLICT",
              "IDEMPOTENCY_REUSE_ATTEMPT_MISMATCH",
              "MARKETPLACE_RESERVATION_INVALID_STATUS" ->
          MarketplaceWeb3AutoSettleSkipCategory.LOCK_OR_STATE_RACE;
      default -> MarketplaceWeb3AutoSettleSkipCategory.UNKNOWN_STATE_SKIP;
    };
  }
}
