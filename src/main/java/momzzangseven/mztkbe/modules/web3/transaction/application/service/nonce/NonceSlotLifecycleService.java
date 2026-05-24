package momzzangseven.mztkbe.modules.web3.transaction.application.service.nonce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceEvidenceCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.ReserveSponsorNonceSlotCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceEvidenceView;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotReservation;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.VerifyUnbroadcastableAttemptCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.ManageNonceSlotLifecycleUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.LoadSponsorNonceSlotsPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.RecordSponsorNonceEvidencePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.RecordSponsorNonceSlotTransitionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.ReserveSponsorNonceSlotPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.VerifyUnbroadcastableAttemptPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceEvidenceSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NonceSlotLifecycleService implements ManageNonceSlotLifecycleUseCase {

  private static final String EXTERNAL_ADMIN_CONSUMED_PROOF = "EXTERNAL_ADMIN_CONSUMED_PROOF";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final ReserveSponsorNonceSlotPort reserveSponsorNonceSlotPort;
  private final RecordSponsorNonceSlotTransitionPort recordSponsorNonceSlotTransitionPort;
  private final RecordSponsorNonceEvidencePort recordSponsorNonceEvidencePort;
  private final VerifyUnbroadcastableAttemptPort verifyUnbroadcastableAttemptPort;
  private final LoadSponsorNonceSlotsPort loadSponsorNonceSlotsPort;

  @Transactional
  public SponsorNonceSlotReservation reserve(ReserveSponsorNonceSlotCommand command) {
    return reserveSponsorNonceSlotPort.reserve(command);
  }

  @Transactional
  public SponsorNonceEvidenceView recordEvidence(RecordSponsorNonceEvidenceCommand command) {
    validateEvidence(command);
    return recordSponsorNonceEvidencePort.record(command);
  }

  @Transactional
  public SponsorNonceSlotView transition(RecordSponsorNonceSlotTransitionCommand command) {
    validateTransition(command);
    return recordSponsorNonceSlotTransitionPort.recordTransition(command);
  }

  @Transactional(readOnly = true)
  public boolean verifyUnbroadcastable(VerifyUnbroadcastableAttemptCommand command) {
    return verifyUnbroadcastableAttemptPort.verifyUnbroadcastable(command);
  }

  @Transactional(readOnly = true)
  public List<SponsorNonceSlotView> loadSlotsForReview(long chainId, String fromAddress) {
    requireValidScope(chainId, fromAddress, 0L);
    return loadSponsorNonceSlotsPort.loadSlotsForReview(
        chainId, EvmAddress.of(fromAddress).value());
  }

  private void validateEvidence(RecordSponsorNonceEvidenceCommand command) {
    requireValidScope(command.chainId(), command.fromAddress(), command.nonce());
    if (command.source() == SponsorNonceEvidenceSource.ADMIN
        && (command.createdBy() == null || command.createdBy().isBlank())) {
      throw new Web3InvalidInputException("createdBy is required for admin evidence");
    }
    switch (command.evidenceType()) {
      case RPC_SNAPSHOT, UNKNOWN_CONSUMED_CLOSURE ->
          requireSource(command, SponsorNonceEvidenceSource.SYSTEM);
      case ADMIN_UNKNOWN_CONSUMED_CLOSURE, ADMIN_CONSUMED_PROOF, DATA_CORRECTION ->
          requireSource(command, SponsorNonceEvidenceSource.ADMIN);
      case UNKNOWN_TO_CONSUMED_CORRECTION -> {
        if (command.relatedEvidenceId() == null || command.relatedEvidenceId() <= 0) {
          throw new Web3InvalidInputException(
              "relatedEvidenceId is required for unknown-consumed correction");
        }
      }
    }
    JsonNode payload = parsePayload(command.payloadJson());
    validatePayloadSchema(command, payload);
  }

  private void validateTransition(RecordSponsorNonceSlotTransitionCommand command) {
    requireValidScope(command.getChainId(), command.getFromAddress(), command.getNonce());
    if (command.getFromStatus() == null || command.getToStatus() == null) {
      throw new Web3InvalidInputException("fromStatus and toStatus are required");
    }
    if (!command.getFromStatus().canTransitionTo(command.getToStatus())) {
      throw new Web3TransactionStateInvalidException(
          "unsupported nonce slot transition: "
              + command.getFromStatus()
              + " -> "
              + command.getToStatus());
    }
    if (command.getStateChangedAt() == null) {
      throw new Web3InvalidInputException("stateChangedAt is required");
    }

    switch (command.getToStatus()) {
      case DROPPED -> validateDropped(command);
      case CONSUMED -> validateConsumed(command);
      case CONSUMED_UNKNOWN -> validateConsumedUnknown(command);
      case STUCK -> requireNonBlank(command.getStuckReason(), "stuckReason is required");
      case REPLACEMENT_PREPARING -> validateReplacementPreparing(command);
      case BROADCASTING -> validateBroadcasting(command);
      case OPERATOR_REVIEW_REQUIRED ->
          requireNonBlank(command.getTerminalReason(), "terminalReason is required");
      default -> validateMetadataCleared(command);
    }
  }

  private void validateDropped(RecordSponsorNonceSlotTransitionCommand command) {
    if (command.hasRawTx()
        || command.hasTxHash()
        || command.hasSigningEvidence()
        || command.hasBroadcastEvidence()) {
      throw new Web3TransactionStateInvalidException(
          "DROPPED requires proof that no chain-reachable evidence exists");
    }
    if (command.getReleasedAttemptId() != null || command.getActiveAttemptId() != null) {
      requirePositive(
          command.getReleasedAttemptId() != null
              ? command.getReleasedAttemptId()
              : command.getActiveAttemptId(),
          "releasedAttemptId or activeAttemptId must be positive");
    }
    requirePositive(
        command.getReleasedTxId() != null ? command.getReleasedTxId() : command.getActiveTxId(),
        "releasedTxId or activeTxId must be positive");
    requireNonBlank(command.getReleaseReason(), "releaseReason is required");
  }

  private void validateConsumed(RecordSponsorNonceSlotTransitionCommand command) {
    boolean backendOwned =
        command.getConsumedAttemptId() != null
            || command.getConsumedTxId() != null
            || command.hasReceiptEvidence();
    if (backendOwned) {
      if (command.getConsumedAttemptId() != null || command.getActiveAttemptId() != null) {
        requirePositive(
            command.getConsumedAttemptId() != null
                ? command.getConsumedAttemptId()
                : command.getActiveAttemptId(),
            "consumedAttemptId or activeAttemptId must be positive");
      }
      requirePositive(
          command.getConsumedTxId() != null ? command.getConsumedTxId() : command.getActiveTxId(),
          "consumedTxId or activeTxId must be positive");
      if (!command.hasReceiptEvidence()) {
        throw new Web3TransactionStateInvalidException(
            "backend-owned CONSUMED requires receipt evidence");
      }
      if (command.getConsumedExternalEvidenceId() != null) {
        throw new Web3TransactionStateInvalidException(
            "backend-owned CONSUMED must clear consumedExternalEvidenceId");
      }
      return;
    }

    requirePositive(
        command.getConsumedExternalEvidenceId(),
        "external-proof CONSUMED requires consumedExternalEvidenceId");
    if (!EXTERNAL_ADMIN_CONSUMED_PROOF.equals(command.getTerminalReason())) {
      throw new Web3TransactionStateInvalidException(
          "external-proof CONSUMED requires admin consumed proof terminal reason");
    }
  }

  private void validateConsumedUnknown(RecordSponsorNonceSlotTransitionCommand command) {
    requirePositive(
        command.getConsumedExternalEvidenceId(),
        "CONSUMED_UNKNOWN requires consumedExternalEvidenceId");
    requireNonBlank(command.getTerminalReason(), "terminalReason is required");
    if (command.getConsumedAttemptId() != null || command.getConsumedTxId() != null) {
      throw new Web3TransactionStateInvalidException(
          "CONSUMED_UNKNOWN must not set consumed attempt or transaction ids");
    }
  }

  private void validateReplacementPreparing(RecordSponsorNonceSlotTransitionCommand command) {
    requireNonBlank(command.getReplacementClaimOwner(), "replacementClaimOwner is required");
    if (command.getReplacementClaimExpiresAt() == null
        || !command.getReplacementClaimExpiresAt().isAfter(LocalDateTime.MIN)) {
      throw new Web3InvalidInputException("replacementClaimExpiresAt is required");
    }
    if (command.getReplacementPrepareAttemptCount() <= 0) {
      throw new Web3InvalidInputException("replacementPrepareAttemptCount must be positive");
    }
    requireNonBlank(command.getStuckReason(), "stuckReason is required");
  }

  private void validateBroadcasting(RecordSponsorNonceSlotTransitionCommand command) {
    requireNonBlank(
        command.getBroadcastRecoveryClaimOwner(), "broadcastRecoveryClaimOwner is required");
    requireNonBlank(
        command.getBroadcastRecoveryClaimToken(), "broadcastRecoveryClaimToken is required");
    if (command.getBroadcastRecoveryClaimExpiresAt() == null) {
      throw new Web3InvalidInputException("broadcastRecoveryClaimExpiresAt is required");
    }
    if (command.getBroadcastRecoveryAttemptCount() <= 0) {
      throw new Web3InvalidInputException("broadcastRecoveryAttemptCount must be positive");
    }
    if (!command.hasTxHash() && command.getActiveTxId() == null) {
      throw new Web3TransactionStateInvalidException(
          "BROADCASTING requires an active signed transaction");
    }
  }

  private void validateMetadataCleared(RecordSponsorNonceSlotTransitionCommand command) {
    if (command.getStuckReason() != null
        || command.getReplacementClaimOwner() != null
        || command.getReplacementClaimExpiresAt() != null
        || command.getReplacementPrepareAttemptCount() != 0
        || command.getBroadcastRecoveryClaimOwner() != null
        || command.getBroadcastRecoveryClaimToken() != null
        || command.getBroadcastRecoveryClaimExpiresAt() != null
        || command.getBroadcastRecoveryAttemptCount() != 0) {
      throw new Web3TransactionStateInvalidException(
          "status-scoped recovery metadata must be cleared for " + command.getToStatus());
    }
  }

  private void requireValidScope(long chainId, String fromAddress, long nonce) {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    EvmAddress.of(fromAddress);
    if (nonce < 0) {
      throw new Web3InvalidInputException("nonce must be >= 0");
    }
  }

  private void requireSource(
      RecordSponsorNonceEvidenceCommand command, SponsorNonceEvidenceSource expectedSource) {
    if (command.source() != expectedSource) {
      throw new Web3InvalidInputException(
          command.evidenceType() + " evidence must use source " + expectedSource);
    }
  }

  private void requirePositive(Long value, String message) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(message);
    }
  }

  private void requireNonBlank(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException(message);
    }
  }

  private JsonNode parsePayload(String payloadJson) {
    try {
      JsonNode payload = OBJECT_MAPPER.readTree(payloadJson);
      if (payload == null || !payload.isObject()) {
        throw new Web3InvalidInputException("payloadJson must be a JSON object");
      }
      return payload;
    } catch (IOException e) {
      throw new Web3InvalidInputException("payloadJson must be valid JSON");
    }
  }

  private void validatePayloadSchema(RecordSponsorNonceEvidenceCommand command, JsonNode payload) {
    JsonNode payloadProviderAlias = payload.get("providerAlias");
    if (payloadProviderAlias != null && !payloadProviderAlias.isNull()) {
      if (command.providerAlias() == null
          || !command.providerAlias().equals(payloadProviderAlias.asText())) {
        throw new Web3InvalidInputException("payload providerAlias must match providerAlias");
      }
    }

    switch (command.evidenceType()) {
      case RPC_SNAPSHOT -> requirePayloadFields(payload, "chainPendingNonce", "chainLatestNonce");
      case UNKNOWN_CONSUMED_CLOSURE, ADMIN_UNKNOWN_CONSUMED_CLOSURE ->
          requirePayloadFields(payload, "chainLatestNonce", "closureReason");
      case ADMIN_CONSUMED_PROOF -> requirePayloadFields(payload, "txHash", "outcome");
      case UNKNOWN_TO_CONSUMED_CORRECTION ->
          requirePayloadFields(payload, "correctionReason", "resolution");
      case DATA_CORRECTION -> requirePayloadFields(payload, "correctionReason");
    }
  }

  private void requirePayloadFields(JsonNode payload, String... fieldNames) {
    for (String fieldName : fieldNames) {
      if (!payload.hasNonNull(fieldName)) {
        throw new Web3InvalidInputException("payloadJson missing required field: " + fieldName);
      }
    }
  }
}
