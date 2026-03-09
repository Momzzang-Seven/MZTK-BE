package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.AppProvider;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.Test;

class VerificationRequestEntityTest {

  @Test
  void fromMapsDomainFieldsIntoEntity() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 3, 8, 10, 0);
    VerificationRequest domain = sampleDomain(createdAt);

    VerificationRequestEntity entity = VerificationRequestEntity.from(domain);

    assertThat(entity.getId()).isEqualTo(11L);
    assertThat(entity.getVerificationId()).isEqualTo("verification-11");
    assertThat(entity.getUserId()).isEqualTo(7L);
    assertThat(entity.getVerificationKind()).isEqualTo(VerificationKind.WORKOUT_RECORD);
    assertThat(entity.getAppProvider()).isEqualTo(AppProvider.NIKE_RUN);
    assertThat(entity.getStatus()).isEqualTo(VerificationStatus.RETRY_SCHEDULED);
    assertThat(entity.getExerciseDate()).isEqualTo(LocalDate.of(2026, 3, 8));
    assertThat(entity.getTempObjectKey()).isEqualTo("temp/object-key");
    assertThat(entity.getTempObjectExpiresAt()).isEqualTo(createdAt.plusHours(6));
    assertThat(entity.getImageContentType()).isEqualTo("image/png");
    assertThat(entity.getImageSizeBytes()).isEqualTo(4096L);
    assertThat(entity.getImageSha256()).isEqualTo("sha-256");
    assertThat(entity.getRequestFingerprint()).isEqualTo("fingerprint");
    assertThat(entity.getConfidenceScore()).isEqualByComparingTo("0.91");
    assertThat(entity.getRejectionReasonCode()).isEqualTo(RejectionReasonCode.DATE_MISMATCH);
    assertThat(entity.getRejectionReasonDetail()).isEqualTo("visible date is stale");
    assertThat(entity.getFailureCode()).isEqualTo(FailureCode.EXTERNAL_AI_TIMEOUT);
    assertThat(entity.getRetryCount()).isEqualTo(2);
    assertThat(entity.getNextRetryAt()).isEqualTo(createdAt.plusMinutes(5));
    assertThat(entity.getAnalysisStartedAt()).isEqualTo(createdAt.plusMinutes(1));
    assertThat(entity.getAnalysisCompletedAt()).isEqualTo(createdAt.plusMinutes(2));
    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    assertThat(entity.getUpdatedAt()).isEqualTo(createdAt.plusMinutes(3));
  }

  @Test
  void toDomainMapsEntityFieldsBackIntoDomain() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 3, 8, 10, 0);
    VerificationRequestEntity entity = sampleEntity(createdAt);

    VerificationRequest domain = entity.toDomain();

    assertThat(domain.getId()).isEqualTo(11L);
    assertThat(domain.getVerificationId()).isEqualTo("verification-11");
    assertThat(domain.getUserId()).isEqualTo(7L);
    assertThat(domain.getVerificationKind()).isEqualTo(VerificationKind.WORKOUT_RECORD);
    assertThat(domain.getAppProvider()).isEqualTo(AppProvider.NIKE_RUN);
    assertThat(domain.getStatus()).isEqualTo(VerificationStatus.RETRY_SCHEDULED);
    assertThat(domain.getExerciseDate()).isEqualTo(LocalDate.of(2026, 3, 8));
    assertThat(domain.getTempObjectKey()).isEqualTo("temp/object-key");
    assertThat(domain.getTempObjectExpiresAt()).isEqualTo(createdAt.plusHours(6));
    assertThat(domain.getImageContentType()).isEqualTo("image/png");
    assertThat(domain.getImageSizeBytes()).isEqualTo(4096L);
    assertThat(domain.getImageSha256()).isEqualTo("sha-256");
    assertThat(domain.getRequestFingerprint()).isEqualTo("fingerprint");
    assertThat(domain.getConfidenceScore()).isEqualByComparingTo("0.91");
    assertThat(domain.getRejectionReasonCode()).isEqualTo(RejectionReasonCode.DATE_MISMATCH);
    assertThat(domain.getRejectionReasonDetail()).isEqualTo("visible date is stale");
    assertThat(domain.getFailureCode()).isEqualTo(FailureCode.EXTERNAL_AI_TIMEOUT);
    assertThat(domain.getRetryCount()).isEqualTo(2);
    assertThat(domain.getNextRetryAt()).isEqualTo(createdAt.plusMinutes(5));
    assertThat(domain.getAnalysisStartedAt()).isEqualTo(createdAt.plusMinutes(1));
    assertThat(domain.getAnalysisCompletedAt()).isEqualTo(createdAt.plusMinutes(2));
    assertThat(domain.getCreatedAt()).isEqualTo(createdAt);
    assertThat(domain.getUpdatedAt()).isEqualTo(createdAt.plusMinutes(3));
  }

  @Test
  void onCreateAssignsCreatedAtAndUpdatedAtWhenMissing() {
    VerificationRequestEntity entity =
        VerificationRequestEntity.builder()
            .verificationId("verification-1")
            .userId(7L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.PENDING)
            .imageContentType("image/jpeg")
            .imageSizeBytes(10L)
            .imageSha256("sha")
            .requestFingerprint("fp")
            .retryCount(0)
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
  }

  @Test
  void onCreatePreservesCreatedAtAndOnlyBackfillsUpdatedAt() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 3, 8, 9, 30);
    VerificationRequestEntity entity =
        VerificationRequestEntity.builder()
            .verificationId("verification-2")
            .userId(7L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.PENDING)
            .imageContentType("image/jpeg")
            .imageSizeBytes(10L)
            .imageSha256("sha")
            .requestFingerprint("fp")
            .retryCount(0)
            .createdAt(createdAt)
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    assertThat(entity.getUpdatedAt()).isEqualTo(createdAt);
  }

  @Test
  void onUpdateRefreshesUpdatedAt() {
    LocalDateTime beforeUpdate = LocalDateTime.of(2026, 3, 8, 9, 30);
    VerificationRequestEntity entity = sampleEntity(beforeUpdate);

    entity.onUpdate();

    assertThat(entity.getUpdatedAt()).isAfter(beforeUpdate.plusMinutes(3));
  }

  @Test
  void querydslTypeExposesExpectedMetadata() {
    QVerificationRequestEntity root = QVerificationRequestEntity.verificationRequestEntity;
    QVerificationRequestEntity fromPath = new QVerificationRequestEntity(root);
    QVerificationRequestEntity fromMetadata = new QVerificationRequestEntity(forVariable("custom"));

    assertThat(root.userId.getMetadata().getName()).isEqualTo("userId");
    assertThat(root.status.getMetadata().getName()).isEqualTo("status");
    assertThat(fromPath.getType()).isEqualTo(VerificationRequestEntity.class);
    assertThat(fromMetadata.getMetadata().getName()).isEqualTo("custom");
  }

  private VerificationRequest sampleDomain(LocalDateTime createdAt) {
    return VerificationRequest.builder()
        .id(11L)
        .verificationId("verification-11")
        .userId(7L)
        .verificationKind(VerificationKind.WORKOUT_RECORD)
        .appProvider(AppProvider.NIKE_RUN)
        .status(VerificationStatus.RETRY_SCHEDULED)
        .exerciseDate(LocalDate.of(2026, 3, 8))
        .tempObjectKey("temp/object-key")
        .tempObjectExpiresAt(createdAt.plusHours(6))
        .imageContentType("image/png")
        .imageSizeBytes(4096L)
        .imageSha256("sha-256")
        .requestFingerprint("fingerprint")
        .confidenceScore(BigDecimal.valueOf(0.91))
        .rejectionReasonCode(RejectionReasonCode.DATE_MISMATCH)
        .rejectionReasonDetail("visible date is stale")
        .failureCode(FailureCode.EXTERNAL_AI_TIMEOUT)
        .retryCount(2)
        .nextRetryAt(createdAt.plusMinutes(5))
        .analysisStartedAt(createdAt.plusMinutes(1))
        .analysisCompletedAt(createdAt.plusMinutes(2))
        .createdAt(createdAt)
        .updatedAt(createdAt.plusMinutes(3))
        .build();
  }

  private VerificationRequestEntity sampleEntity(LocalDateTime createdAt) {
    return VerificationRequestEntity.builder()
        .id(11L)
        .verificationId("verification-11")
        .userId(7L)
        .verificationKind(VerificationKind.WORKOUT_RECORD)
        .appProvider(AppProvider.NIKE_RUN)
        .status(VerificationStatus.RETRY_SCHEDULED)
        .exerciseDate(LocalDate.of(2026, 3, 8))
        .tempObjectKey("temp/object-key")
        .tempObjectExpiresAt(createdAt.plusHours(6))
        .imageContentType("image/png")
        .imageSizeBytes(4096L)
        .imageSha256("sha-256")
        .requestFingerprint("fingerprint")
        .confidenceScore(BigDecimal.valueOf(0.91))
        .rejectionReasonCode(RejectionReasonCode.DATE_MISMATCH)
        .rejectionReasonDetail("visible date is stale")
        .failureCode(FailureCode.EXTERNAL_AI_TIMEOUT)
        .retryCount(2)
        .nextRetryAt(createdAt.plusMinutes(5))
        .analysisStartedAt(createdAt.plusMinutes(1))
        .analysisCompletedAt(createdAt.plusMinutes(2))
        .createdAt(createdAt)
        .updatedAt(createdAt.plusMinutes(3))
        .build();
  }
}
