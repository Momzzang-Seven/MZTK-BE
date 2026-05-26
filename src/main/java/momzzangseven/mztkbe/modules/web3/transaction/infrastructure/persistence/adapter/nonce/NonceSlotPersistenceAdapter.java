package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter.nonce;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionNotFoundException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceEvidenceCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.ReserveSponsorNonceSlotCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceEvidenceView;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotReservation;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.VerifyUnbroadcastableAttemptCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.LoadSponsorNonceSlotsPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.RecordSponsorNonceEvidencePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.RecordSponsorNonceSlotTransitionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.ReserveSponsorNonceSlotPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.VerifyUnbroadcastableAttemptPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceAttemptStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlot;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce.NonceSlotAttemptEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce.NonceSlotEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce.NonceSlotEvidenceEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.nonce.NonceSlotAttemptJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.nonce.NonceSlotEvidenceJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.nonce.NonceSlotJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class NonceSlotPersistenceAdapter
    implements LoadSponsorNonceSlotsPort,
        ReserveSponsorNonceSlotPort,
        RecordSponsorNonceSlotTransitionPort,
        RecordSponsorNonceEvidencePort,
        VerifyUnbroadcastableAttemptPort {

  private static final EnumSet<SponsorNonceSlotStatus> COORDINATION_VISIBLE_STATUSES =
      EnumSet.of(
          SponsorNonceSlotStatus.RESERVED,
          SponsorNonceSlotStatus.REPLACEMENT_PREPARING,
          SponsorNonceSlotStatus.SIGNED,
          SponsorNonceSlotStatus.BROADCASTING,
          SponsorNonceSlotStatus.BROADCASTED,
          SponsorNonceSlotStatus.STUCK,
          SponsorNonceSlotStatus.OPERATOR_REVIEW_REQUIRED);

  private final NonceSlotJpaRepository slotRepository;
  private final NonceSlotAttemptJpaRepository attemptRepository;
  private final NonceSlotEvidenceJpaRepository evidenceRepository;
  private final Web3TransactionJpaRepository transactionRepository;
  private final TransactionRewardTokenProperties rewardTokenProperties;
  private final Clock appClock;

  @Override
  @Transactional(readOnly = true)
  public List<SponsorNonceSlot> loadOpenOrBlockingSlots(long chainId, String fromAddress) {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    String normalizedAddress = EvmAddress.of(fromAddress).value();
    int scanLimit = rewardTokenProperties.getWorker().getCoordinationVisibleSlotScanLimit();
    List<NonceSlotEntity> slots =
        slotRepository.findByScopeAndStatusInOrderByNonce(
            chainId,
            normalizedAddress,
            COORDINATION_VISIBLE_STATUSES,
            PageRequest.of(0, scanLimit));
    if (slots.size() >= scanLimit) {
      log.warn(
          "sponsor nonce coordination visible slot scan reached limit: chainId={}, fromAddress={}, limit={}",
          chainId,
          normalizedAddress,
          scanLimit);
    }
    Map<Long, Web3TransactionEntity> activeTransactions = loadActiveTransactions(slots);
    return slots.stream().map(slot -> toDomainSlot(slot, activeTransactions)).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SponsorNonceSlotView> loadSlotForReview(
      long chainId, String fromAddress, long nonce) {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    String normalizedAddress = EvmAddress.of(fromAddress).value();
    return slotRepository.findByScope(chainId, normalizedAddress, nonce).map(this::toView);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SponsorNonceSlotView> loadSlotsForReview(
      long chainId, String fromAddress, int page, int size) {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    if (page < 0) {
      throw new Web3InvalidInputException("page must be zero or positive");
    }
    if (size <= 0) {
      throw new Web3InvalidInputException("size must be positive");
    }
    String normalizedAddress = EvmAddress.of(fromAddress).value();
    return slotRepository
        .findByScopeOrderByNonce(chainId, normalizedAddress, PageRequest.of(page, size))
        .stream()
        .map(this::toView)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<SponsorNonceSlotView> loadSlotsForReview(long chainId, String fromAddress) {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    String normalizedAddress = EvmAddress.of(fromAddress).value();
    return slotRepository.findByScopeOrderByNonce(chainId, normalizedAddress).stream()
        .map(this::toView)
        .toList();
  }

  @Override
  @Transactional
  public SponsorNonceSlotReservation reserve(ReserveSponsorNonceSlotCommand command) {
    String normalizedAddress = EvmAddress.of(command.fromAddress()).value();
    Web3TransactionEntity transaction = loadTransaction(command.transactionId());
    boolean nonceAssigned =
        validateTransactionScope(
            transaction, command.chainId(), normalizedAddress, command.nonce());
    if (nonceAssigned) {
      transactionRepository.saveAndFlush(transaction);
    }

    NonceSlotEntity slot =
        slotRepository
            .findByScopeForUpdate(command.chainId(), normalizedAddress, command.nonce())
            .orElse(null);
    if (slot != null && slot.getStatus() != SponsorNonceSlotStatus.DROPPED) {
      throw new Web3TransactionStateInvalidException(
          "nonce slot is already active: chainId="
              + command.chainId()
              + ", fromAddress="
              + normalizedAddress
              + ", nonce="
              + command.nonce()
              + ", status="
              + slot.getStatus());
    }

    int attemptNo =
        attemptRepository
                .findTopByChainIdAndFromAddressAndNonceOrderByAttemptNoDesc(
                    command.chainId(), normalizedAddress, command.nonce())
                .map(NonceSlotAttemptEntity::getAttemptNo)
                .orElse(0)
            + 1;

    NonceSlotAttemptEntity attempt =
        attemptRepository.saveAndFlush(
            NonceSlotAttemptEntity.builder()
                .chainId(command.chainId())
                .fromAddress(normalizedAddress)
                .nonce(command.nonce())
                .attemptNo(attemptNo)
                .txId(command.transactionId())
                .status(SponsorNonceAttemptStatus.RESERVED)
                .idempotencyKey(command.idempotencyKey())
                .createdAt(command.now())
                .updatedAt(command.now())
                .build());

    NonceSlotEntity savedSlot =
        slotRepository.saveAndFlush(
            slot == null
                ? newReservedSlot(command, normalizedAddress, attemptNo, attempt.getId())
                : reactivateDroppedSlot(slot, command, attemptNo, attempt.getId()));
    return new SponsorNonceSlotReservation(
        savedSlot.getChainId(),
        savedSlot.getFromAddress(),
        savedSlot.getNonce(),
        savedSlot.getAttemptNo(),
        savedSlot.getActiveAttemptId(),
        savedSlot.getActiveTxId(),
        savedSlot.getStatus());
  }

  @Override
  @Transactional
  public SponsorNonceSlotView recordTransition(RecordSponsorNonceSlotTransitionCommand command) {
    String normalizedAddress = EvmAddress.of(command.getFromAddress()).value();
    NonceSlotEntity slot =
        slotRepository
            .findByScopeForUpdate(command.getChainId(), normalizedAddress, command.getNonce())
            .orElseThrow(
                () ->
                    new Web3TransactionStateInvalidException(
                        "nonce slot not found: chainId="
                            + command.getChainId()
                            + ", fromAddress="
                            + normalizedAddress
                            + ", nonce="
                            + command.getNonce()));
    if (slot.getStatus() != command.getFromStatus()) {
      throw new Web3TransactionStateInvalidException(
          "stale nonce slot transition: expected="
              + command.getFromStatus()
              + ", actual="
              + slot.getStatus());
    }

    validateTransitionOwnership(slot, command);
    applyTransition(slot, command);
    updateAttemptStatus(slot, command);
    return toView(slotRepository.saveAndFlush(slot));
  }

  @Override
  @Transactional
  public SponsorNonceEvidenceView record(RecordSponsorNonceEvidenceCommand command) {
    String normalizedAddress = EvmAddress.of(command.fromAddress()).value();
    if (command.relatedEvidenceId() != null) {
      evidenceRepository
          .findByIdAndChainIdAndFromAddressAndNonce(
              command.relatedEvidenceId(), command.chainId(), normalizedAddress, command.nonce())
          .orElseThrow(
              () ->
                  new Web3TransactionStateInvalidException(
                      "related nonce evidence must be in the same scope"));
    }
    NonceSlotEvidenceEntity saved =
        evidenceRepository.saveAndFlush(
            NonceSlotEvidenceEntity.builder()
                .chainId(command.chainId())
                .fromAddress(normalizedAddress)
                .nonce(command.nonce())
                .evidenceType(command.evidenceType())
                .evidenceSource(command.source())
                .providerAlias(command.providerAlias())
                .payloadJson(command.payloadJson())
                .relatedEvidenceId(command.relatedEvidenceId())
                .createdBy(command.createdBy())
                .observedAt(command.observedAt())
                .build());
    return toEvidenceView(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean verifyUnbroadcastable(VerifyUnbroadcastableAttemptCommand command) {
    String normalizedAddress = EvmAddress.of(command.fromAddress()).value();
    return attemptRepository
        .findByIdAndChainIdAndFromAddressAndNonce(
            command.attemptId(), command.chainId(), normalizedAddress, command.nonce())
        .map(attempt -> isUnbroadcastable(attempt, command.now()))
        .orElse(false);
  }

  private NonceSlotEntity newReservedSlot(
      ReserveSponsorNonceSlotCommand command,
      String normalizedAddress,
      int attemptNo,
      Long attemptId) {
    return NonceSlotEntity.builder()
        .chainId(command.chainId())
        .fromAddress(normalizedAddress)
        .nonce(command.nonce())
        .status(SponsorNonceSlotStatus.RESERVED)
        .attemptNo(attemptNo)
        .activeAttemptId(attemptId)
        .activeTxId(command.transactionId())
        .replacementPrepareAttemptCount(0)
        .broadcastRecoveryAttemptCount(0)
        .createdAt(command.now())
        .updatedAt(command.now())
        .build();
  }

  private NonceSlotEntity reactivateDroppedSlot(
      NonceSlotEntity slot, ReserveSponsorNonceSlotCommand command, int attemptNo, Long attemptId) {
    slot.setStatus(SponsorNonceSlotStatus.RESERVED);
    slot.setAttemptNo(attemptNo);
    slot.setActiveAttemptId(attemptId);
    slot.setActiveTxId(command.transactionId());
    slot.setActiveTxHash(null);
    slot.setConsumedAttemptId(null);
    slot.setConsumedTxId(null);
    slot.setConsumedExternalEvidenceId(null);
    slot.setConsumedAt(null);
    slot.setConsumedReason(null);
    slot.setReleasedAttemptId(null);
    slot.setReleasedTxId(null);
    slot.setReleasedAt(null);
    slot.setReleaseReason(null);
    slot.setStuckReason(null);
    clearReplacementClaim(slot);
    clearBroadcastRecoveryClaim(slot);
    slot.setUpdatedAt(command.now());
    return slot;
  }

  private void applyTransition(
      NonceSlotEntity slot, RecordSponsorNonceSlotTransitionCommand command) {
    SponsorNonceSlotStatus toStatus = command.getToStatus();
    LocalDateTime changedAt = command.getStateChangedAt();
    slot.setStatus(toStatus);
    if (command.getActiveAttemptId() != null) {
      slot.setActiveAttemptId(command.getActiveAttemptId());
    }
    if (command.getActiveTxId() != null) {
      slot.setActiveTxId(command.getActiveTxId());
    }

    switch (toStatus) {
      case DROPPED -> applyDropped(slot, command, changedAt);
      case CONSUMED -> applyConsumed(slot, command, changedAt);
      case CONSUMED_UNKNOWN -> applyConsumedUnknown(slot, command, changedAt);
      case STUCK -> {
        slot.setStuckReason(command.getStuckReason());
        clearReplacementClaim(slot);
        clearBroadcastRecoveryClaim(slot);
      }
      case REPLACEMENT_PREPARING -> {
        slot.setStuckReason(command.getStuckReason());
        slot.setReplacementClaimOwner(command.getReplacementClaimOwner());
        slot.setReplacementClaimExpiresAt(command.getReplacementClaimExpiresAt());
        slot.setReplacementPrepareAttemptCount(command.getReplacementPrepareAttemptCount());
        clearBroadcastRecoveryClaim(slot);
      }
      case BROADCASTING -> {
        slot.setBroadcastStartedAt(changedAt);
        slot.setBroadcastRecoveryClaimOwner(command.getBroadcastRecoveryClaimOwner());
        slot.setBroadcastRecoveryClaimToken(command.getBroadcastRecoveryClaimToken());
        slot.setBroadcastRecoveryClaimExpiresAt(command.getBroadcastRecoveryClaimExpiresAt());
        slot.setBroadcastRecoveryAttemptCount(command.getBroadcastRecoveryAttemptCount());
        clearReplacementClaim(slot);
      }
      case BROADCASTED -> {
        slot.setLastBroadcastedAt(changedAt);
        clearReplacementClaim(slot);
        clearBroadcastRecoveryClaim(slot);
      }
      default -> {
        clearReplacementClaim(slot);
        clearBroadcastRecoveryClaim(slot);
        slot.setStuckReason(null);
      }
    }
    syncTransactionEvidence(slot);
    slot.setUpdatedAt(changedAt);
  }

  private void applyDropped(
      NonceSlotEntity slot,
      RecordSponsorNonceSlotTransitionCommand command,
      LocalDateTime changedAt) {
    slot.setReleasedAttemptId(
        command.getReleasedAttemptId() == null
            ? slot.getActiveAttemptId()
            : command.getReleasedAttemptId());
    slot.setReleasedTxId(
        command.getReleasedTxId() == null ? slot.getActiveTxId() : command.getReleasedTxId());
    slot.setReleasedAt(changedAt);
    slot.setReleaseReason(command.getReleaseReason());
    slot.setActiveAttemptId(null);
    slot.setActiveTxId(null);
    slot.setActiveTxHash(null);
    slot.setBroadcastStartedAt(null);
    slot.setLastBroadcastedAt(null);
    slot.setStuckReason(null);
    clearReplacementClaim(slot);
    clearBroadcastRecoveryClaim(slot);
  }

  private void applyConsumed(
      NonceSlotEntity slot,
      RecordSponsorNonceSlotTransitionCommand command,
      LocalDateTime changedAt) {
    slot.setConsumedAttemptId(
        command.getConsumedAttemptId() == null
            ? slot.getActiveAttemptId()
            : command.getConsumedAttemptId());
    slot.setConsumedTxId(
        command.getConsumedTxId() == null ? slot.getActiveTxId() : command.getConsumedTxId());
    slot.setConsumedExternalEvidenceId(command.getConsumedExternalEvidenceId());
    slot.setConsumedAt(changedAt);
    slot.setConsumedReason(command.getConsumedReason());
    slot.setStuckReason(null);
    clearReplacementClaim(slot);
    clearBroadcastRecoveryClaim(slot);
  }

  private void applyConsumedUnknown(
      NonceSlotEntity slot,
      RecordSponsorNonceSlotTransitionCommand command,
      LocalDateTime changedAt) {
    slot.setConsumedAttemptId(null);
    slot.setConsumedTxId(null);
    slot.setConsumedExternalEvidenceId(command.getConsumedExternalEvidenceId());
    slot.setConsumedAt(changedAt);
    slot.setConsumedReason(command.getConsumedReason());
    slot.setStuckReason(null);
    clearReplacementClaim(slot);
    clearBroadcastRecoveryClaim(slot);
  }

  private void updateAttemptStatus(
      NonceSlotEntity slot, RecordSponsorNonceSlotTransitionCommand command) {
    Long attemptId = authoritativeAttemptId(slot, command);
    if (attemptId == null) {
      return;
    }
    NonceSlotAttemptEntity attempt =
        attemptRepository
            .findByIdAndChainIdAndFromAddressAndNonce(
                attemptId, slot.getChainId(), slot.getFromAddress(), slot.getNonce())
            .orElseThrow(
                () ->
                    new Web3TransactionStateInvalidException(
                        "authoritative attempt is missing or outside nonce slot scope"));
    SponsorNonceAttemptStatus attemptStatus = resolveAttemptStatus(slot, command);
    attempt.setStatus(attemptStatus);
    attempt.setTerminalReason(resolveTerminalReason(attemptStatus, command));
    syncAttemptTransactionEvidence(slot, attempt);
    if (slot.getStatus() == SponsorNonceSlotStatus.SIGNED) {
      attempt.setSignedAt(command.getStateChangedAt());
    }
    if (slot.getStatus() == SponsorNonceSlotStatus.BROADCASTING) {
      attempt.setBroadcastStartedAt(command.getStateChangedAt());
    }
    if (slot.getStatus() == SponsorNonceSlotStatus.BROADCASTED) {
      attempt.setBroadcastedAt(command.getStateChangedAt());
    }
    if (slot.getStatus() == SponsorNonceSlotStatus.CONSUMED && command.hasReceiptEvidence()) {
      attempt.setReceiptObservedAt(command.getStateChangedAt());
      attempt.setReceiptStatus("FOUND");
    }
    attempt.setUpdatedAt(command.getStateChangedAt());
    attemptRepository.saveAndFlush(attempt);
  }

  private SponsorNonceAttemptStatus resolveAttemptStatus(
      NonceSlotEntity slot, RecordSponsorNonceSlotTransitionCommand command) {
    if (slot.getStatus() == SponsorNonceSlotStatus.CONSUMED
        && command.getConsumedAttemptId() == null
        && command.getConsumedTxId() == null
        && command.getConsumedExternalEvidenceId() != null) {
      return SponsorNonceAttemptStatus.ABANDONED;
    }
    return SponsorNonceAttemptStatus.fromSlotStatus(slot.getStatus());
  }

  private String resolveTerminalReason(
      SponsorNonceAttemptStatus attemptStatus, RecordSponsorNonceSlotTransitionCommand command) {
    if (attemptStatus == SponsorNonceAttemptStatus.CONSUMED) {
      return null;
    }
    if (command.getTerminalReason() != null && !command.getTerminalReason().isBlank()) {
      return command.getTerminalReason();
    }
    if (command.getReleaseReason() != null && !command.getReleaseReason().isBlank()) {
      return command.getReleaseReason();
    }
    if (command.getStuckReason() != null && !command.getStuckReason().isBlank()) {
      return command.getStuckReason();
    }
    return command.getConsumedReason();
  }

  private Long authoritativeAttemptId(
      NonceSlotEntity slot, RecordSponsorNonceSlotTransitionCommand command) {
    if (command.getToStatus() == SponsorNonceSlotStatus.DROPPED) {
      return command.getReleasedAttemptId() == null
          ? slot.getReleasedAttemptId()
          : command.getReleasedAttemptId();
    }
    if (command.getToStatus() == SponsorNonceSlotStatus.CONSUMED
        && command.getConsumedAttemptId() != null) {
      return command.getConsumedAttemptId();
    }
    if (command.getActiveAttemptId() != null) {
      return command.getActiveAttemptId();
    }
    return slot.getActiveAttemptId();
  }

  private void validateTransitionOwnership(
      NonceSlotEntity slot, RecordSponsorNonceSlotTransitionCommand command) {
    validateMatchingId("activeAttemptId", slot.getActiveAttemptId(), command.getActiveAttemptId());
    validateMatchingId("activeTxId", slot.getActiveTxId(), command.getActiveTxId());

    if (command.getToStatus() == SponsorNonceSlotStatus.DROPPED) {
      validateMatchingId(
          "releasedAttemptId", slot.getActiveAttemptId(), command.getReleasedAttemptId());
      validateMatchingId("releasedTxId", slot.getActiveTxId(), command.getReleasedTxId());
      return;
    }

    if (command.getToStatus() == SponsorNonceSlotStatus.CONSUMED) {
      validateMatchingId(
          "consumedAttemptId", slot.getActiveAttemptId(), command.getConsumedAttemptId());
      validateMatchingId("consumedTxId", slot.getActiveTxId(), command.getConsumedTxId());
    }
  }

  private void validateMatchingId(String fieldName, Long current, Long requested) {
    if (current == null || requested == null || current.equals(requested)) {
      return;
    }
    throw new Web3TransactionStateInvalidException(
        "nonce slot " + fieldName + " mismatch: expected=" + current + ", requested=" + requested);
  }

  private void syncTransactionEvidence(NonceSlotEntity slot) {
    if (slot.getActiveTxId() == null || slot.getStatus() == SponsorNonceSlotStatus.DROPPED) {
      return;
    }
    transactionRepository
        .findById(slot.getActiveTxId())
        .map(Web3TransactionEntity::getTxHash)
        .filter(txHash -> txHash != null && !txHash.isBlank())
        .ifPresent(slot::setActiveTxHash);
  }

  private void syncAttemptTransactionEvidence(
      NonceSlotEntity slot, NonceSlotAttemptEntity attempt) {
    if (attempt.getTxId() == null || slot.getStatus() == SponsorNonceSlotStatus.DROPPED) {
      return;
    }
    transactionRepository
        .findById(attempt.getTxId())
        .map(Web3TransactionEntity::getTxHash)
        .filter(txHash -> txHash != null && !txHash.isBlank())
        .ifPresent(attempt::setTxHash);
  }

  private boolean isUnbroadcastable(NonceSlotAttemptEntity attempt, LocalDateTime now) {
    if (attempt.getTxHash() != null
        || attempt.getSignedAt() != null
        || attempt.getBroadcastStartedAt() != null
        || attempt.getBroadcastedAt() != null) {
      return false;
    }
    return transactionRepository
        .findById(attempt.getTxId())
        .map(
            tx ->
                tx.getStatus() == Web3TxStatus.CREATED
                    && !isProcessingActive(tx, now)
                    && tx.getTxHash() == null
                    && tx.getSignedRawTx() == null
                    && tx.getSignedAt() == null
                    && tx.getBroadcastedAt() == null)
        .orElse(false);
  }

  private boolean isProcessingActive(Web3TransactionEntity transaction, LocalDateTime now) {
    if (transaction.getProcessingUntil() != null && transaction.getProcessingUntil().isAfter(now)) {
      return true;
    }
    return transaction.getProcessingBy() != null
        && !transaction.getProcessingBy().isBlank()
        && transaction.getProcessingUntil() == null;
  }

  private SponsorNonceSlot toDomainSlot(NonceSlotEntity entity) {
    return toDomainSlot(entity, Map.of());
  }

  private SponsorNonceSlot toDomainSlot(
      NonceSlotEntity entity, Map<Long, Web3TransactionEntity> activeTransactions) {
    SponsorNonceSlot.Builder builder =
        SponsorNonceSlot.builder(
            entity.getChainId(), entity.getFromAddress(), entity.getNonce(), entity.getStatus());
    if (entity.getActiveTxHash() != null) {
      builder.txHash().signingEvidence();
    }
    applyTransactionEvidence(entity, builder, activeTransactions);
    if (entity.getBroadcastStartedAt() != null || entity.getLastBroadcastedAt() != null) {
      builder.broadcastEvidence();
    }
    if (entity.getConsumedAttemptId() != null || entity.getConsumedTxId() != null) {
      builder.receiptEvidence();
    }
    if (entity.getConsumedExternalEvidenceId() != null) {
      builder.retainedExternalEvidence();
    }
    if (isTimedOut(entity)) {
      builder.timedOut();
    }
    if (isReplacementEligible(entity)) {
      builder.replacementEligible();
    }
    return builder.build();
  }

  private Map<Long, Web3TransactionEntity> loadActiveTransactions(List<NonceSlotEntity> slots) {
    List<Long> activeTxIds =
        slots.stream()
            .map(NonceSlotEntity::getActiveTxId)
            .filter(id -> id != null)
            .distinct()
            .toList();
    if (activeTxIds.isEmpty()) {
      return Map.of();
    }
    return transactionRepository.findByIdIn(activeTxIds).stream()
        .collect(Collectors.toMap(Web3TransactionEntity::getId, Function.identity()));
  }

  private void applyTransactionEvidence(
      NonceSlotEntity entity,
      SponsorNonceSlot.Builder builder,
      Map<Long, Web3TransactionEntity> activeTransactions) {
    if (entity.getActiveTxId() == null) {
      return;
    }
    Web3TransactionEntity tx = activeTransactions.get(entity.getActiveTxId());
    if (tx == null && activeTransactions.isEmpty()) {
      tx = transactionRepository.findById(entity.getActiveTxId()).orElse(null);
    }
    if (tx == null) {
      return;
    }
    if (tx.getSignedRawTx() != null && !tx.getSignedRawTx().isBlank()) {
      builder.rawTx().signingEvidence();
    }
    if (tx.getSignedAt() != null) {
      builder.signingEvidence();
    }
    if (tx.getTxHash() != null && !tx.getTxHash().isBlank()) {
      builder.txHash();
    }
    if (tx.getBroadcastedAt() != null) {
      builder.broadcastEvidence();
    }
  }

  private boolean isTimedOut(NonceSlotEntity entity) {
    LocalDateTime baseline =
        switch (entity.getStatus()) {
          case RESERVED -> entity.getUpdatedAt();
          case BROADCASTING -> entity.getBroadcastStartedAt();
          case BROADCASTED ->
              entity.getLastBroadcastedAt() == null
                  ? entity.getBroadcastStartedAt()
                  : entity.getLastBroadcastedAt();
          default -> null;
        };
    if (baseline == null) {
      return false;
    }
    Duration timeout =
        switch (entity.getStatus()) {
          case RESERVED, BROADCASTING ->
              Duration.ofSeconds(rewardTokenProperties.getWorker().getClaimTtlSeconds());
          case BROADCASTED ->
              Duration.ofSeconds(rewardTokenProperties.getWorker().getReceiptTimeoutSeconds());
          default -> Duration.ZERO;
        };
    return !baseline.plus(timeout).isAfter(LocalDateTime.now(appClock));
  }

  private boolean isReplacementEligible(NonceSlotEntity entity) {
    return entity.getStatus() == SponsorNonceSlotStatus.BROADCASTED
        && isTimedOut(entity)
        && entity.getActiveTxHash() != null
        && !entity.getActiveTxHash().isBlank()
        && entity.getLastBroadcastedAt() != null;
  }

  private SponsorNonceSlotView toView(NonceSlotEntity entity) {
    return new SponsorNonceSlotView(
        entity.getChainId(),
        entity.getFromAddress(),
        entity.getNonce(),
        entity.getStatus(),
        entity.getAttemptNo(),
        entity.getActiveAttemptId(),
        entity.getActiveTxId(),
        entity.getActiveTxHash(),
        entity.getConsumedAttemptId(),
        entity.getConsumedTxId(),
        entity.getConsumedExternalEvidenceId(),
        entity.getConsumedAt(),
        entity.getConsumedReason(),
        entity.getReleasedAttemptId(),
        entity.getReleasedTxId(),
        entity.getReleasedAt(),
        entity.getReleaseReason(),
        entity.getStuckReason(),
        entity.getReplacementClaimOwner(),
        entity.getReplacementClaimExpiresAt(),
        entity.getReplacementPrepareAttemptCount(),
        entity.getBroadcastStartedAt(),
        entity.getLastBroadcastedAt(),
        entity.getBroadcastRecoveryClaimOwner(),
        entity.getBroadcastRecoveryClaimExpiresAt(),
        entity.getBroadcastRecoveryAttemptCount(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private SponsorNonceEvidenceView toEvidenceView(NonceSlotEvidenceEntity entity) {
    return new SponsorNonceEvidenceView(
        entity.getId(),
        entity.getChainId(),
        entity.getFromAddress(),
        entity.getNonce(),
        entity.getEvidenceType(),
        entity.getEvidenceSource(),
        entity.getProviderAlias(),
        entity.getPayloadJson(),
        entity.getRelatedEvidenceId(),
        entity.getCreatedBy(),
        entity.getObservedAt(),
        entity.getCreatedAt());
  }

  private Web3TransactionEntity loadTransaction(Long transactionId) {
    return transactionRepository
        .findById(transactionId)
        .orElseThrow(() -> new Web3TransactionNotFoundException(transactionId));
  }

  private boolean validateTransactionScope(
      Web3TransactionEntity transaction, long chainId, String fromAddress, long nonce) {
    if (!Long.valueOf(chainId).equals(transaction.getChainId())) {
      throw new Web3TransactionStateInvalidException("slot transaction chainId mismatch");
    }
    if (transaction.getStatus() != Web3TxStatus.CREATED) {
      throw new Web3TransactionStateInvalidException(
          "slot transaction can only reserve nonce in CREATED status");
    }
    if (!fromAddress.equals(EvmAddress.of(transaction.getFromAddress()).value())) {
      throw new Web3TransactionStateInvalidException("slot transaction sender mismatch");
    }
    if (transaction.getNonce() == null) {
      if (transaction.getStatus() != Web3TxStatus.CREATED) {
        throw new Web3TransactionStateInvalidException(
            "slot transaction nonce can only be assigned in CREATED status");
      }
      transaction.setNonce(nonce);
      return true;
    }
    if (!Long.valueOf(nonce).equals(transaction.getNonce())) {
      throw new Web3TransactionStateInvalidException("slot transaction nonce mismatch");
    }
    return false;
  }

  private void clearReplacementClaim(NonceSlotEntity slot) {
    slot.setReplacementClaimOwner(null);
    slot.setReplacementClaimExpiresAt(null);
    slot.setReplacementPrepareAttemptCount(0);
  }

  private void clearBroadcastRecoveryClaim(NonceSlotEntity slot) {
    slot.setBroadcastRecoveryClaimOwner(null);
    slot.setBroadcastRecoveryClaimToken(null);
    slot.setBroadcastRecoveryClaimExpiresAt(null);
    slot.setBroadcastRecoveryAttemptCount(0);
  }
}
