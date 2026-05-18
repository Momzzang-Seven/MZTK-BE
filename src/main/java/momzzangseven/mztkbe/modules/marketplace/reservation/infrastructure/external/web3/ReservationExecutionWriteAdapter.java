package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentStateQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentStateResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayConfirmedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentStateUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ReplayConfirmedExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceTokenMovement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Cross-module adapter for owner-scoped shared execution recovery reads. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
@ConditionalOnBean({
  GetExecutionIntentUseCase.class,
  GetExecutionIntentStateUseCase.class,
  ReplayConfirmedExecutionIntentUseCase.class
})
public class ReservationExecutionWriteAdapter
    implements LoadReservationExecutionWritePort,
        LoadReservationExecutionStatePort,
        ReplayConfirmedReservationExecutionPort {

  private final GetExecutionIntentUseCase getExecutionIntentUseCase;
  private final GetExecutionIntentStateUseCase getExecutionIntentStateUseCase;
  private final ReplayConfirmedExecutionIntentUseCase replayConfirmedExecutionIntentUseCase;
  private final ObjectMapper objectMapper;

  @Override
  public ReservationExecutionWriteView load(Long requesterUserId, String executionIntentId) {
    return toView(
        getExecutionIntentUseCase.execute(
            new GetExecutionIntentQuery(requesterUserId, executionIntentId)));
  }

  @Override
  public ReservationExecutionStateView loadState(String executionIntentId) {
    return toStateView(
        getExecutionIntentStateUseCase.execute(
            new GetExecutionIntentStateQuery(executionIntentId)));
  }

  @Override
  public boolean replayConfirmed(String executionIntentId, String expectedActionType) {
    return replayConfirmedExecutionIntentUseCase.execute(
        new ReplayConfirmedExecutionIntentCommand(executionIntentId, expectedActionType));
  }

  private ReservationExecutionWriteView toView(GetExecutionIntentResult result) {
    MarketplaceEscrowExecutionPayload payload = marketplacePayload(result.payloadSnapshotJson());
    return new ReservationExecutionWriteView(
        new ReservationExecutionWriteView.Resource(
            result.resourceType().name(), result.resourceId(), result.resourceStatus().name()),
        result.actionType().name(),
        null,
        new ReservationExecutionWriteView.ExecutionIntent(
            result.executionIntentId(),
            result.executionIntentStatus().name(),
            result.expiresAt(),
            result.expiresAtEpochSeconds()),
        new ReservationExecutionWriteView.Execution(result.mode().name(), result.signCount()),
        toSignRequest(result.signRequest()),
        result.signRequestUnavailableReason() == null
            ? null
            : result.signRequestUnavailableReason().name(),
        false,
        toSignatureMeta(payload, result),
        toTokenMovement(payload == null ? null : payload.tokenMovement()));
  }

  private MarketplaceEscrowExecutionPayload marketplacePayload(String payloadSnapshotJson) {
    if (payloadSnapshotJson == null || payloadSnapshotJson.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(payloadSnapshotJson, MarketplaceEscrowExecutionPayload.class);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  private ReservationExecutionWriteView.SignatureMeta toSignatureMeta(
      MarketplaceEscrowExecutionPayload payload, GetExecutionIntentResult result) {
    if (payload == null || payload.signedAt() == null) {
      return null;
    }
    long expiresAt = result.expiresAtEpochSeconds();
    if (expiresAt <= payload.signedAt()) {
      return null;
    }
    return new ReservationExecutionWriteView.SignatureMeta(payload.signedAt(), expiresAt);
  }

  private ReservationExecutionWriteView.TokenMovement toTokenMovement(
      MarketplaceTokenMovement movement) {
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

  private ReservationExecutionStateView toStateView(GetExecutionIntentStateResult result) {
    return new ReservationExecutionStateView(
        result.executionIntentId(),
        result.executionIntentStatus().name(),
        result.actionType().name(),
        result.requesterUserId());
  }

  private ReservationExecutionWriteView.SignRequest toSignRequest(SignRequestBundle request) {
    if (request == null) {
      return null;
    }
    return new ReservationExecutionWriteView.SignRequest(
        toAuthorization(request.authorization()),
        toSubmit(request.submit()),
        toTransaction(request.transaction()));
  }

  private ReservationExecutionWriteView.Authorization toAuthorization(
      SignRequestBundle.AuthorizationSignRequest request) {
    if (request == null) {
      return null;
    }
    return new ReservationExecutionWriteView.Authorization(
        request.chainId(),
        request.delegateTarget(),
        request.authorityNonce(),
        request.payloadHashToSign());
  }

  private ReservationExecutionWriteView.Submit toSubmit(
      SignRequestBundle.SubmitSignRequest request) {
    if (request == null) {
      return null;
    }
    return new ReservationExecutionWriteView.Submit(
        request.executionDigest(), request.deadlineEpochSeconds());
  }

  private ReservationExecutionWriteView.Transaction toTransaction(
      SignRequestBundle.TransactionSignRequest request) {
    if (request == null) {
      return null;
    }
    return new ReservationExecutionWriteView.Transaction(
        request.chainId(),
        request.fromAddress(),
        request.toAddress(),
        request.valueHex(),
        request.data(),
        request.nonce(),
        request.gasLimitHex(),
        request.maxPriorityFeePerGasHex(),
        request.maxFeePerGasHex(),
        request.expectedNonce());
  }
}
