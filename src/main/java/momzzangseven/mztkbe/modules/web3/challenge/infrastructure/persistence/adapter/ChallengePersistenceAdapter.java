package momzzangseven.mztkbe.modules.web3.challenge.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.out.LoadChallengePort;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.out.SaveChallengePort;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.Challenge;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengePurpose;
import momzzangseven.mztkbe.modules.web3.challenge.infrastructure.entity.ChallengeEntity;
import momzzangseven.mztkbe.modules.web3.challenge.infrastructure.persistence.repository.ChallengeJpaRepository;
import org.springframework.stereotype.Component;

/**
 * Challenge Persistence Adapter
 *
 * <p>Implements both SaveChallengePort and LoadChallengePort for Challenge persistence operations.
 *
 * <p>Hexagonal Architecture: Infrastructure Layer (Outbound Adapter)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengePersistenceAdapter implements SaveChallengePort, LoadChallengePort {

  private final ChallengeJpaRepository repository;

  // ========================================
  // SaveChallengePort Implementation
  // ========================================

  @Override
  public Challenge save(Challenge challenge) {
    log.debug("Saving challenge: nonce={}", challenge.getNonce());
    ChallengeEntity entity = mapToEntity(challenge);
    ChallengeEntity savedEntity = repository.save(entity);
    return mapToDomain(savedEntity);
  }

  // ========================================
  // LoadChallengePort Implementation
  // ========================================

  @Override
  public Optional<Challenge> findByNonce(String nonce) {
    log.debug("Finding challenge by nonce: {}", nonce);
    return repository.findByNonce(nonce).map(this::mapToDomain);
  }

  @Override
  public Optional<Challenge> findByNonceAndPurpose(String nonce, ChallengePurpose purpose) {
    log.debug("Finding challenge by nonce and purpose: nonce={}, purpose={}", nonce, purpose);
    return repository.findByNonceAndPurpose(nonce, purpose).map(this::mapToDomain);
  }

  // ========== Mapping Methods ==========
  private ChallengeEntity mapToEntity(Challenge challenge) {
    return ChallengeEntity.builder()
        .nonce(challenge.getNonce())
        .userId(challenge.getUserId())
        .purpose(challenge.getPurpose())
        .walletAddress(challenge.getWalletAddress())
        .message(challenge.getMessage())
        .status(challenge.getStatus())
        .expiresAt(challenge.getExpiresAt())
        .createdAt(challenge.getCreatedAt())
        .usedAt(challenge.getUsedAt())
        .build();
  }

  private Challenge mapToDomain(ChallengeEntity entity) {
    return Challenge.builder()
        .nonce(entity.getNonce())
        .userId(entity.getUserId())
        .purpose(entity.getPurpose())
        .walletAddress(entity.getWalletAddress())
        .message(entity.getMessage())
        .status(entity.getStatus())
        .expiresAt(entity.getExpiresAt())
        .createdAt(entity.getCreatedAt())
        .usedAt(entity.getUsedAt())
        .build();
  }
}
