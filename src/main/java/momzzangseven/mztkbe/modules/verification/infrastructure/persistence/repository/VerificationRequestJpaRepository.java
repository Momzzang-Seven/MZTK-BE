package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity.VerificationRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VerificationRequestJpaRepository
    extends JpaRepository<VerificationRequestEntity, Long> {

  Optional<VerificationRequestEntity> findByVerificationId(String verificationId);

  @Query(
      """
      select verificationRequest
      from VerificationRequestEntity verificationRequest
      where verificationRequest.userId = :userId
        and verificationRequest.createdAt >= :slotStart
        and verificationRequest.createdAt < :slotEndExclusive
        and verificationRequest.status in :statuses
      order by verificationRequest.createdAt desc
      """)
  Optional<VerificationRequestEntity> findLatestBlockingRequestForSlot(
      @Param("userId") Long userId,
      @Param("slotStart") LocalDateTime slotStart,
      @Param("slotEndExclusive") LocalDateTime slotEndExclusive,
      @Param("statuses") Collection<VerificationStatus> statuses);

  boolean existsByUserIdAndVerificationKindAndRequestFingerprint(
      Long userId, VerificationKind verificationKind, String requestFingerprint);

  Optional<VerificationRequestEntity> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
