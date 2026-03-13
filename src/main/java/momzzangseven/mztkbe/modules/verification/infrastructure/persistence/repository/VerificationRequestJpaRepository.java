package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.repository;

import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity.VerificationRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationRequestJpaRepository
    extends JpaRepository<VerificationRequestEntity, Long> {

  Optional<VerificationRequestEntity> findByTmpObjectKey(String tmpObjectKey);

  Optional<VerificationRequestEntity> findByVerificationId(String verificationId);

  Optional<VerificationRequestEntity> findByVerificationIdAndUserId(
      String verificationId, Long userId);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from VerificationRequestEntity v where v.tmpObjectKey = :tmpObjectKey")
  Optional<VerificationRequestEntity> findByTmpObjectKeyForUpdate(
      @Param("tmpObjectKey") String tmpObjectKey);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from VerificationRequestEntity v where v.verificationId = :verificationId")
  Optional<VerificationRequestEntity> findByVerificationIdForUpdate(
      @Param("verificationId") String verificationId);

  Optional<VerificationRequestEntity>
      findFirstByUserIdAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThanOrderByUpdatedAtDesc(
          Long userId, java.time.Instant start, java.time.Instant end);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          update verification_requests
             set status = 'ANALYZING',
                 failure_code = null,
                 updated_at = CURRENT_TIMESTAMP
           where verification_id = :verificationId
             and status = 'FAILED'
          """,
      nativeQuery = true)
  int transitionFailedToAnalyzing(@Param("verificationId") String verificationId);
}
