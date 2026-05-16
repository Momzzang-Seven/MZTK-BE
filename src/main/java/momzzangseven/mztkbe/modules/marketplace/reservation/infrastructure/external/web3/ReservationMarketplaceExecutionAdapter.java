package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceSignRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.PrepareMarketplaceUserExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceActorType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceAllowanceStrategy;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Reservation infrastructure adapter that bridges reservation user actions to marketplace Web3.
 *
 * <p>The bean is only active when marketplace execution preparation has a real implementation.
 */
@Component
@ConditionalOnBean(PrepareMarketplaceUserExecutionUseCase.class)
@RequiredArgsConstructor
public class ReservationMarketplaceExecutionAdapter
    implements PrepareReservationEscrowExecutionPort {

  private final PrepareMarketplaceUserExecutionUseCase prepareMarketplaceUserExecutionUseCase;

  @Override
  public PrepareReservationEscrowResult preparePurchase(PrepareReservationEscrowCommand command) {
    return prepare(command, MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE);
  }

  @Override
  public PrepareReservationEscrowResult prepareCancel(PrepareReservationEscrowCommand command) {
    return prepare(command, MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL);
  }

  @Override
  public PrepareReservationEscrowResult prepareConfirm(PrepareReservationEscrowCommand command) {
    return prepare(command, MarketplaceExecutionActionType.MARKETPLACE_CLASS_CONFIRM);
  }

  @Override
  public PrepareReservationEscrowResult prepareDeadlineRefund(
      PrepareReservationEscrowCommand command) {
    return prepare(command, MarketplaceExecutionActionType.MARKETPLACE_CLASS_EXPIRED_REFUND);
  }

  private PrepareReservationEscrowResult prepare(
      PrepareReservationEscrowCommand command, MarketplaceExecutionActionType actionType) {
    MarketplaceExecutionIntentResult result =
        prepareMarketplaceUserExecutionUseCase.prepare(toRequest(command, actionType));
    return new PrepareReservationEscrowResult(toView(result));
  }

  private MarketplaceEscrowExecutionRequest toRequest(
      PrepareReservationEscrowCommand command, MarketplaceExecutionActionType actionType) {
    String resourceId = String.valueOf(command.reservationId());
    return new MarketplaceEscrowExecutionRequest(
        actionType,
        command.reservationId(),
        resourceId,
        command.orderId(),
        command.orderKey(),
        MarketplaceActorType.valueOf(command.actorType()),
        command.authorityUserId(),
        command.requesterUserId(),
        command.counterpartyUserId(),
        command.buyerUserId(),
        command.trainerUserId(),
        command.reservationVersion(),
        command.expectedReservationStatus() == null
            ? null
            : command.expectedReservationStatus().name(),
        command.expectedEscrowStatus() == null ? null : command.expectedEscrowStatus().name(),
        command.buyerWalletAddress(),
        command.trainerWalletAddress(),
        command.tokenAddress(),
        new java.math.BigInteger(command.priceBaseUnits()),
        MarketplaceAllowanceStrategy.PRE_EXISTING_ALLOWANCE,
        command.bookedPriceAmountKrw(),
        command.sessionEndAt(),
        command.expectedContractDeadlineEpochSeconds(),
        command.contractDeadlineEpochSeconds(),
        command.pendingAttemptToken(),
        command.targetTerminalStatus());
  }

  private ReservationExecutionWriteView toView(MarketplaceExecutionIntentResult result) {
    return new ReservationExecutionWriteView(
        new ReservationExecutionWriteView.Resource(
            result.resource().type(), result.resource().id(), result.resource().status()),
        result.actionType(),
        result.orderKey(),
        new ReservationExecutionWriteView.ExecutionIntent(
            result.executionIntent().id(),
            result.executionIntent().status(),
            result.executionIntent().expiresAt(),
            result.executionIntent().expiresAtEpochSeconds()),
        new ReservationExecutionWriteView.Execution(
            result.execution().mode(), result.execution().signCount()),
        toSignRequest(result.signRequest()),
        result.signRequestUnavailableReason(),
        result.existing(),
        toSignatureMeta(result.signatureMeta()),
        toTokenMovement(result.tokenMovement()));
  }

  private ReservationExecutionWriteView.SignRequest toSignRequest(MarketplaceSignRequest request) {
    if (request == null) {
      return null;
    }
    return new ReservationExecutionWriteView.SignRequest(
        toAuthorization(request.authorization()),
        toSubmit(request.submit()),
        toTransaction(request.transaction()));
  }

  private ReservationExecutionWriteView.Authorization toAuthorization(
      MarketplaceSignRequest.Authorization authorization) {
    if (authorization == null) {
      return null;
    }
    return new ReservationExecutionWriteView.Authorization(
        authorization.chainId(),
        authorization.delegateTarget(),
        authorization.authorityNonce(),
        authorization.payloadHashToSign());
  }

  private ReservationExecutionWriteView.Submit toSubmit(MarketplaceSignRequest.Submit submit) {
    if (submit == null) {
      return null;
    }
    return new ReservationExecutionWriteView.Submit(
        submit.executionDigest(), submit.deadlineEpochSeconds());
  }

  private ReservationExecutionWriteView.Transaction toTransaction(
      MarketplaceSignRequest.Transaction transaction) {
    if (transaction == null) {
      return null;
    }
    return new ReservationExecutionWriteView.Transaction(
        transaction.chainId(),
        transaction.fromAddress(),
        transaction.toAddress(),
        transaction.valueHex(),
        transaction.data(),
        transaction.nonce(),
        transaction.gasLimitHex(),
        transaction.maxPriorityFeePerGasHex(),
        transaction.maxFeePerGasHex(),
        transaction.expectedNonce());
  }

  private ReservationExecutionWriteView.SignatureMeta toSignatureMeta(
      momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceSignatureMeta meta) {
    if (meta == null) {
      return null;
    }
    return new ReservationExecutionWriteView.SignatureMeta(
        meta.signedAt(), meta.signatureExpiresAt());
  }

  private ReservationExecutionWriteView.TokenMovement toTokenMovement(
      momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceTokenMovement
          movement) {
    if (movement == null) {
      return null;
    }
    return new ReservationExecutionWriteView.TokenMovement(
        movement.tokenAddress(),
        movement.amountBaseUnits().toString(),
        movement.fromRole(),
        movement.fromAddress(),
        movement.toRole(),
        movement.toAddress());
  }
}
