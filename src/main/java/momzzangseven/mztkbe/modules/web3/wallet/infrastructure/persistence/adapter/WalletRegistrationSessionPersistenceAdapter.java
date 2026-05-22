package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.adapter;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.wallet.application.exception.DuplicateWalletRegistrationSessionException;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.CreateWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationRecoveryCandidatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity.WalletRegistrationSessionEntity;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.repository.WalletRegistrationSessionJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Persistence adapter for wallet registration sessions. */
@Component
@RequiredArgsConstructor
public class WalletRegistrationSessionPersistenceAdapter
    implements CreateWalletRegistrationSessionPort,
        SaveWalletRegistrationSessionPort,
        LoadWalletRegistrationSessionPort,
        LockWalletRegistrationSessionPort,
        LoadWalletRegistrationRecoveryCandidatePort {

  private static final EnumSet<WalletRegistrationStatus> NON_TERMINAL_STATUSES =
      EnumSet.of(
          WalletRegistrationStatus.APPROVAL_REQUIRED,
          WalletRegistrationStatus.APPROVAL_SIGNED,
          WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN,
          WalletRegistrationStatus.APPROVAL_RETRYABLE,
          WalletRegistrationStatus.FINALIZATION_FAILED,
          WalletRegistrationStatus.LOCAL_CONFLICT);
  private static final EnumSet<WalletRegistrationStatus> RECOVERY_CANDIDATE_STATUSES =
      EnumSet.of(
          WalletRegistrationStatus.APPROVAL_REQUIRED,
          WalletRegistrationStatus.APPROVAL_SIGNED,
          WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN,
          WalletRegistrationStatus.APPROVAL_RETRYABLE,
          WalletRegistrationStatus.FINALIZATION_FAILED,
          WalletRegistrationStatus.LOCAL_CONFLICT);
  private static final EnumSet<WalletRegistrationStatus> AUTHORITATIVE_NEWER_STATUSES =
      EnumSet.of(
          WalletRegistrationStatus.APPROVAL_REQUIRED,
          WalletRegistrationStatus.APPROVAL_SIGNED,
          WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN,
          WalletRegistrationStatus.APPROVAL_RETRYABLE,
          WalletRegistrationStatus.FINALIZATION_FAILED,
          WalletRegistrationStatus.LOCAL_CONFLICT,
          WalletRegistrationStatus.REGISTERED);
  private static final PageRequest ONE_LATEST = PageRequest.of(0, 1);

  private final WalletRegistrationSessionJpaRepository repository;

  @Override
  @Transactional
  public WalletRegistrationSession createAndFlush(WalletRegistrationSession session) {
    try {
      WalletRegistrationSessionEntity saved = repository.saveAndFlush(mapToEntity(session));
      return mapToDomain(saved);
    } catch (DataIntegrityViolationException exception) {
      throw new DuplicateWalletRegistrationSessionException(
          session.getUserId(), session.getWalletAddress(), exception);
    }
  }

  @Override
  public WalletRegistrationSession save(WalletRegistrationSession session) {
    WalletRegistrationSessionEntity saved = repository.save(mapToEntity(session));
    return mapToDomain(saved);
  }

  @Override
  public Optional<WalletRegistrationSession> loadByPublicId(String publicId) {
    return repository.findByPublicId(publicId).map(this::mapToDomain);
  }

  @Override
  public Optional<WalletRegistrationSession> loadByPublicIdAndUserId(String publicId, Long userId) {
    return repository.findByPublicIdAndUserId(publicId, userId).map(this::mapToDomain);
  }

  @Override
  public Optional<WalletRegistrationSession> loadByLatestExecutionIntentId(
      String executionIntentId) {
    return repository.findByLatestExecutionIntentId(executionIntentId).map(this::mapToDomain);
  }

  @Override
  public Optional<WalletRegistrationSession> loadLatestNonTerminalByUserId(Long userId) {
    return repository
        .findLatestByUserIdAndStatusIn(userId, NON_TERMINAL_STATUSES, ONE_LATEST)
        .stream()
        .findFirst()
        .map(this::mapToDomain);
  }

  @Override
  public Optional<WalletRegistrationSession> loadLatestNonTerminalByWalletAddress(
      String walletAddress) {
    return repository
        .findLatestByWalletAddressAndStatusIn(walletAddress, NON_TERMINAL_STATUSES, ONE_LATEST)
        .stream()
        .findFirst()
        .map(this::mapToDomain);
  }

  @Override
  public Optional<WalletRegistrationSession> loadLatestNonTerminalByUserIdAndWalletAddress(
      Long userId, String walletAddress) {
    return repository
        .findLatestByUserIdAndWalletAddressAndStatusIn(
            userId, walletAddress, NON_TERMINAL_STATUSES, ONE_LATEST)
        .stream()
        .findFirst()
        .map(this::mapToDomain);
  }

  @Override
  public boolean existsNewerByUserIdOrWalletAddress(
      Long userId, String walletAddress, Long sessionId) {
    if (userId == null || walletAddress == null || sessionId == null) {
      return false;
    }
    return repository.existsNewerByUserIdOrWalletAddress(
        userId, walletAddress, AUTHORITATIVE_NEWER_STATUSES, sessionId);
  }

  @Override
  public Optional<WalletRegistrationSession> lockByPublicIdForUpdate(String publicId) {
    return repository.findByPublicIdForUpdate(publicId).map(this::mapToDomain);
  }

  @Override
  public List<WalletRegistrationSession> loadRecoveryCandidates(int limit) {
    return repository
        .findByStatusInOrderByUpdatedAtAscIdAsc(
            RECOVERY_CANDIDATE_STATUSES, PageRequest.of(0, limit))
        .stream()
        .map(this::mapToDomain)
        .toList();
  }

  private WalletRegistrationSession mapToDomain(WalletRegistrationSessionEntity entity) {
    return WalletRegistrationSession.builder()
        .id(entity.getId())
        .publicId(entity.getPublicId())
        .userId(entity.getUserId())
        .walletAddress(entity.getWalletAddress())
        .challengeNonce(entity.getChallengeNonce())
        .status(entity.getStatus())
        .latestExecutionIntentId(entity.getLatestExecutionIntentId())
        .receiptTimeoutExecutionIntentIds(entity.getReceiptTimeoutExecutionIntentIds())
        .latestTransactionId(entity.getLatestTransactionId())
        .latestTransactionHash(entity.getLatestTransactionHash())
        .lastExecutionStatus(entity.getLastExecutionStatus())
        .lastErrorCode(entity.getLastErrorCode())
        .lastErrorReason(entity.getLastErrorReason())
        .retryCount(entity.getRetryCount())
        .approvalExpiresAt(entity.getApprovalExpiresAt())
        .submittedAt(entity.getSubmittedAt())
        .confirmedAt(entity.getConfirmedAt())
        .finalizedAt(entity.getFinalizedAt())
        .registeredWalletId(entity.getRegisteredWalletId())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  private WalletRegistrationSessionEntity mapToEntity(WalletRegistrationSession session) {
    return WalletRegistrationSessionEntity.builder()
        .id(session.getId())
        .publicId(session.getPublicId())
        .userId(session.getUserId())
        .walletAddress(session.getWalletAddress())
        .challengeNonce(session.getChallengeNonce())
        .status(session.getStatus())
        .latestExecutionIntentId(session.getLatestExecutionIntentId())
        .receiptTimeoutExecutionIntentIds(session.getReceiptTimeoutExecutionIntentIds())
        .latestTransactionId(session.getLatestTransactionId())
        .latestTransactionHash(session.getLatestTransactionHash())
        .lastExecutionStatus(session.getLastExecutionStatus())
        .lastErrorCode(session.getLastErrorCode())
        .lastErrorReason(session.getLastErrorReason())
        .retryCount(session.getRetryCount())
        .approvalExpiresAt(session.getApprovalExpiresAt())
        .submittedAt(session.getSubmittedAt())
        .confirmedAt(session.getConfirmedAt())
        .finalizedAt(session.getFinalizedAt())
        .registeredWalletId(session.getRegisteredWalletId())
        .createdAt(session.getCreatedAt())
        .updatedAt(session.getUpdatedAt())
        .build();
  }
}
