package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareMarketplaceAdminEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationAdminExecutionDraft;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.SubmitMarketplaceAdminEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.BuildMarketplaceAdminReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitMarketplaceAdminReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionRequestSource;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminExecutionDraftSubmitResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.BuildMarketplaceAdminExecutionDraftUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.SubmitMarketplaceAdminExecutionDraftUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceAdminExecutionRequestSource;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnMarketplaceAdminEnabled
public class ReservationMarketplaceAdminExecutionAdapter
    implements BuildMarketplaceAdminReservationExecutionPort,
        SubmitMarketplaceAdminReservationExecutionPort {

  private final BuildMarketplaceAdminExecutionDraftUseCase
      buildMarketplaceAdminExecutionDraftUseCase;
  private final SubmitMarketplaceAdminExecutionDraftUseCase
      submitMarketplaceAdminExecutionDraftUseCase;

  @Override
  public ReservationAdminExecutionDraft buildRefund(PrepareMarketplaceAdminEscrowCommand command) {
    return new DraftHandle(
        buildMarketplaceAdminExecutionDraftUseCase.execute(
            toRequest(command, MarketplaceExecutionActionType.MARKETPLACE_ADMIN_REFUND)));
  }

  @Override
  public ReservationAdminExecutionDraft buildSettlement(
      PrepareMarketplaceAdminEscrowCommand command) {
    return new DraftHandle(
        buildMarketplaceAdminExecutionDraftUseCase.execute(
            toRequest(command, MarketplaceExecutionActionType.MARKETPLACE_ADMIN_SETTLE)));
  }

  @Override
  public SubmitMarketplaceAdminEscrowResult submit(ReservationAdminExecutionDraft draft) {
    if (!(draft instanceof DraftHandle handle)) {
      throw new Web3InvalidInputException("unsupported marketplace admin execution draft handle");
    }
    MarketplaceAdminExecutionDraftSubmitResult result =
        submitMarketplaceAdminExecutionDraftUseCase.execute(handle.delegate());
    return new SubmitMarketplaceAdminEscrowResult(
        result.executionIntentId(),
        result.executionIntentStatus(),
        result.executionMode(),
        result.expiresAt(),
        result.existing());
  }

  private MarketplaceAdminEscrowExecutionRequest toRequest(
      PrepareMarketplaceAdminEscrowCommand command, MarketplaceExecutionActionType actionType) {
    if (!supports(command.actionType(), actionType)) {
      throw new Web3InvalidInputException("admin action type does not match execution request");
    }
    return new MarketplaceAdminEscrowExecutionRequest(
        actionType,
        command.reservationId(),
        String.valueOf(command.reservationId()),
        command.orderId(),
        command.orderKey(),
        command.requesterUserId(),
        command.counterpartyUserId(),
        command.buyerUserId(),
        command.trainerUserId(),
        command.reservationVersion(),
        command.expectedReservationStatus().name(),
        command.expectedEscrowStatus().name(),
        command.buyerWalletAddress(),
        command.trainerWalletAddress(),
        command.tokenAddress(),
        priceBaseUnits(command.priceBaseUnits()),
        command.bookedPriceAmountKrw(),
        command.sessionEndAt(),
        command.pendingAttemptToken(),
        command.targetTerminalStatus(),
        command.escrowId(),
        command.actionStateId(),
        requestSource(command.requestSource()),
        command.operatorUserId(),
        command.schedulerRunId(),
        command.reasonCode(),
        command.memo(),
        command.rootIdempotencyKey());
  }

  private static BigInteger priceBaseUnits(BigInteger value) {
    if (value == null) {
      throw new Web3InvalidInputException("priceBaseUnits is required");
    }
    return value;
  }

  private static MarketplaceAdminExecutionRequestSource requestSource(
      ReservationActionRequestSource source) {
    return switch (source) {
      case MANUAL_ADMIN -> MarketplaceAdminExecutionRequestSource.MANUAL_ADMIN;
      case SCHEDULER -> MarketplaceAdminExecutionRequestSource.SCHEDULER;
      case USER -> throw new Web3InvalidInputException("USER is not an admin request source");
    };
  }

  private static boolean supports(
      ReservationEscrowAction action, MarketplaceExecutionActionType executionAction) {
    return (action == ReservationEscrowAction.ADMIN_REFUND
            && executionAction == MarketplaceExecutionActionType.MARKETPLACE_ADMIN_REFUND)
        || (action == ReservationEscrowAction.ADMIN_SETTLE
            && executionAction == MarketplaceExecutionActionType.MARKETPLACE_ADMIN_SETTLE);
  }

  private record DraftHandle(MarketplaceExecutionDraft delegate)
      implements ReservationAdminExecutionDraft {}
}
