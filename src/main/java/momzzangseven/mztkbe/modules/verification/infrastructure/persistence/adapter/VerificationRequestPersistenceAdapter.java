package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.adapter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity.VerificationRequestEntity;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.repository.VerificationRequestJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificationRequestPersistenceAdapter implements VerificationRequestPort {

  private final VerificationRequestJpaRepository verificationRequestJpaRepository;
  private final ZoneId appZoneId;

  @Override
  public Optional<VerificationRequest> findByTmpObjectKey(String tmpObjectKey) {
    return verificationRequestJpaRepository
        .findByTmpObjectKey(tmpObjectKey)
        .map(VerificationRequestEntity::toDomain);
  }

  @Override
  public Optional<VerificationRequest> findByVerificationId(String verificationId) {
    return verificationRequestJpaRepository
        .findByVerificationId(verificationId)
        .map(VerificationRequestEntity::toDomain);
  }

  @Override
  public Optional<VerificationRequest> findByVerificationIdAndUserId(
      String verificationId, Long userId) {
    return verificationRequestJpaRepository
        .findByVerificationIdAndUserId(verificationId, userId)
        .map(VerificationRequestEntity::toDomain);
  }

  @Override
  public Optional<VerificationRequest> findByTmpObjectKeyForUpdate(String tmpObjectKey) {
    return verificationRequestJpaRepository
        .findByTmpObjectKeyForUpdate(tmpObjectKey)
        .map(VerificationRequestEntity::toDomain);
  }

  @Override
  public Optional<VerificationRequest> findByVerificationIdForUpdate(String verificationId) {
    return verificationRequestJpaRepository
        .findByVerificationIdForUpdate(verificationId)
        .map(VerificationRequestEntity::toDomain);
  }

  @Override
  public Optional<VerificationRequest> findLatestUpdatedToday(Long userId, LocalDate today) {
    Instant start = today.atStartOfDay(appZoneId).toInstant();
    Instant end = today.plusDays(1).atStartOfDay(appZoneId).toInstant();
    return verificationRequestJpaRepository
        .findFirstByUserIdAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThanOrderByUpdatedAtDesc(
            userId, start, end)
        .map(VerificationRequestEntity::toDomain);
  }

  @Override
  public VerificationRequest save(VerificationRequest request) {
    return verificationRequestJpaRepository
        .save(VerificationRequestEntity.from(request))
        .toDomain();
  }
}
