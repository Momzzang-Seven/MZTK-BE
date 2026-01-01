package momzzangseven.mztkbe.modules.auth.infrastructure.persistence.adapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.token.RefreshTokenNotFoundException;
import momzzangseven.mztkbe.global.error.token.TokenHashingException;
import momzzangseven.mztkbe.modules.auth.application.port.out.DeleteRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.LoadRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.SaveRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;
import momzzangseven.mztkbe.modules.auth.infrastructure.persistence.entity.RefreshTokenEntity;
import momzzangseven.mztkbe.modules.auth.infrastructure.repository.RefreshTokenJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence Adapter for RefreshToken (Infrastructure Layer).
 *
 * <p>Responsibilities: - Implement LoadRefreshTokenPort and SaveRefreshTokenPort - Translate
 * between RefreshToken (domain) and RefreshTokenEntity (JPA) - Hash token values before storing
 * (security) - Use tokenHash for database queries
 *
 * <p>Security Design: - Stores SHA-256 hash in database, not plain token - Original token value is
 * only kept in memory (domain model) - Database breach does not expose valid tokens
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenPersistenceAdapter
    implements LoadRefreshTokenPort, SaveRefreshTokenPort, DeleteRefreshTokenPort {

  private final RefreshTokenJpaRepository repository;

  // ========== LoadRefreshTokenPort Implementation ==========
  @Override
  @Transactional(readOnly = true)
  public Optional<RefreshToken> findByTokenValue(String tokenValue) {
    log.debug("Loading refresh token by value");
    String tokenHash = hashToken(tokenValue);

    return repository.findByTokenHash(tokenHash).map(entity -> mapToDomain(entity, tokenValue));
  }

  @Override
  @Transactional
  public Optional<RefreshToken> findByTokenValueWithLock(String tokenValue) {
    log.debug("Loading and locking refresh token by value");
    String tokenHash = hashToken(tokenValue);

    return repository
        .findByTokenHashWithLock(tokenHash)
        .map(entity -> mapToDomain(entity, tokenValue));
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByTokenValue(String tokenValue) {
    String tokenHash = hashToken(tokenValue);
    return repository.existsByTokenHash(tokenHash);
  }

  // ========== SaveRefreshTokenPort Implementation ==========

  @Override
  @Transactional
  public RefreshToken save(RefreshToken refreshToken) {
    log.debug("Saving refresh token for userId: {}", refreshToken.getUserId());
    String tokenHash = hashToken(refreshToken.getTokenValue());
    RefreshTokenEntity entity;

    if (refreshToken.getId() != null) {
      // UPDATE: Modify existing token
      log.debug("Updating existing token: id={}", refreshToken.getId());

      entity =
          repository
              .findById(refreshToken.getId())
              .orElseThrow(
                  () ->
                      new RefreshTokenNotFoundException(
                          "RefreshToken not found with ID: " + refreshToken.getId()));

      updateEntityFromDomain(entity, refreshToken, tokenHash);

    } else {
      // CREATE: New token
      log.debug("Creating new token for userId: {}", refreshToken.getUserId());

      entity = mapToEntity(refreshToken, tokenHash);
    }

    RefreshTokenEntity savedEntity = repository.save(entity);
    log.debug("Token saved successfully: id={}", savedEntity.getId());

    // Return domain model with original tokenValue
    return mapToDomain(savedEntity, refreshToken.getTokenValue());
  }

  @Override
  @Transactional
  public void deleteByUserId(Long userId) {
    repository.deleteByUserId(userId);
    log.debug("Deleted all refresh tokens for userId: {}", userId);
  }

  @Override
  @Transactional
  public void deleteById(Long refreshTokenId) {
    if (refreshTokenId == null) {
      throw new IllegalArgumentException("refreshTokenId must not be null");
    }
    if (!repository.existsById(refreshTokenId)) {
      throw new RefreshTokenNotFoundException("RefreshToken not found with ID: " + refreshTokenId);
    }
    repository.deleteById(refreshTokenId);
    log.debug("Deleted refresh token: id={}", refreshTokenId);
  }

  @Override
  @Transactional
  public void deleteByUserIdIn(List<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return;
    }
    int deleted = repository.deleteByUserIdIn(userIds);
    log.debug("Deleted refresh tokens: deleted={}, userIds={}", deleted, userIds.size());
  }

  // ========== Mapping Methods (Translator Pattern) ==========

  /** Convert RefreshTokenEntity (Infrastructure) to RefreshToken (Domain). */
  private RefreshToken mapToDomain(RefreshTokenEntity entity, String tokenValue) {
    return RefreshToken.builder()
        .id(entity.getId())
        .userId(entity.getUserId())
        .tokenValue(tokenValue)
        .expiresAt(entity.getExpiresAt())
        .revokedAt(entity.getRevokedAt())
        .createdAt(entity.getCreatedAt())
        .usedAt(entity.getUsedAt())
        .build();
  }

  /**
   * Convert RefreshToken (Domain) to RefreshTokenEntity (Infrastructure). Used for creating new
   * entities.
   */
  private RefreshTokenEntity mapToEntity(RefreshToken token, String tokenHash) {
    return RefreshTokenEntity.builder()
        .id(token.getId())
        .userId(token.getUserId())
        .tokenHash(tokenHash)
        .expiresAt(token.getExpiresAt())
        .revokedAt(token.getRevokedAt())
        .createdAt(token.getCreatedAt())
        .usedAt(token.getUsedAt())
        .build();
  }

  /**
   * Update existing managed entity from domain model. Uses setters to maintain JPA managed state.
   */
  private void updateEntityFromDomain(
      RefreshTokenEntity entity, RefreshToken token, String tokenHash) {

    entity.updateFrom(token, tokenHash);
  }

  // ========== Hash Utility ==========

  /**
   * Hash token value using SHA-256.
   *
   * @param tokenValue Original JWT token
   * @return Hexadecimal hash string (64 characters)
   */
  private String hashToken(String tokenValue) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(tokenValue.getBytes(StandardCharsets.UTF_8));

      // Convert byte array to hex string
      StringBuilder hexString = new StringBuilder();
      for (byte b : hashBytes) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();

    } catch (NoSuchAlgorithmException e) {
      log.error("SHA-256 algorithm not available", e);
      throw new TokenHashingException("Failed to hash refresh token", e);
    }
  }
}
