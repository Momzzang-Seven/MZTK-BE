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
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceEscrowIdCodec;
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

  private PrepareReservationEscrowResult prepare(
      PrepareReservationEscrowCommand command, MarketplaceExecutionActionType actionType) {
    MarketplaceExecutionIntentResult result =
        prepareMarketplaceUserExecutionUseCase.prepare(toRequest(command, actionType));
    return new PrepareReservationEscrowResult(toView(result));
  }

  private MarketplaceEscrowExecutionRequest toRequest(
      PrepareReservationEscrowCommand command, MarketplaceExecutionActionType actionType) {
    String resourceId = String.valueOf(command.reservationId());
    String orderKey = MarketplaceEscrowIdCodec.orderKey(command.reservationId());
    return new MarketplaceEscrowExecutionRequest(
        actionType,
        command.reservationId(),
        resourceId,
        orderKey,
        command.requesterUserId(),
        command.buyerUserId(),
        command.trainerUserId(),
        command.reservationVersion(),
        command.buyerWalletAddress(),
        command.trainerWalletAddress(),
        command.bookedPriceAmountKrw(),
        command.sessionEndAt());
  }

  private ReservationExecutionWriteView toView(MarketplaceExecutionIntentResult result) {
    return new ReservationExecutionWriteView(
        new ReservationExecutionWriteView.Resource(
            result.resource().type(), result.resource().id(), result.resource().status()),
        result.actionType(),
        new ReservationExecutionWriteView.ExecutionIntent(
            result.executionIntent().id(),
            result.executionIntent().status(),
            result.executionIntent().expiresAt()),
        new ReservationExecutionWriteView.Execution(
            result.execution().mode(), result.execution().signCount()),
        toSignRequest(result.signRequest()),
        result.signRequestUnavailableReason(),
        result.existing());
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
}
