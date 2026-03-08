package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.AppProvider;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;

@Entity
@Table(
    name = "verification_requests",
    indexes = {
      @Index(
          name = "idx_verification_user_fingerprint_created",
          columnList = "user_id,request_fingerprint,created_at"),
      @Index(
          name = "idx_verification_user_status_created",
          columnList = "user_id,status,created_at"),
      @Index(
          name = "idx_verification_user_latest",
          columnList = "user_id,analysis_completed_at,created_at"),
      @Index(name = "idx_verification_retry_due", columnList = "status,next_retry_at"),
      @Index(name = "idx_verification_temp_object_expires", columnList = "temp_object_expires_at")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_verification_requests_verification_id",
          columnNames = "verification_id")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VerificationRequestEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "verification_id", nullable = false, length = 36)
  private String verificationId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_kind", nullable = false, length = 30)
  private VerificationKind verificationKind;

  @Enumerated(EnumType.STRING)
  @Column(name = "app_provider", length = 30)
  private AppProvider appProvider;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private VerificationStatus status;

  @Column(name = "exercise_date")
  private LocalDate exerciseDate;

  @Column(name = "temp_object_key", length = 512)
  private String tempObjectKey;

  @Column(name = "temp_object_expires_at")
  private LocalDateTime tempObjectExpiresAt;

  @Column(name = "image_content_type", nullable = false, length = 100)
  private String imageContentType;

  @Column(name = "image_size_bytes", nullable = false)
  private Long imageSizeBytes;

  @Column(name = "image_sha256", nullable = false, length = 64)
  private String imageSha256;

  @Column(name = "request_fingerprint", nullable = false, length = 64)
  private String requestFingerprint;

  @Column(name = "confidence_score", precision = 5, scale = 4)
  private BigDecimal confidenceScore;

  @Enumerated(EnumType.STRING)
  @Column(name = "rejection_reason_code", length = 50)
  private RejectionReasonCode rejectionReasonCode;

  @Column(name = "rejection_reason_detail", length = 255)
  private String rejectionReasonDetail;

  @Enumerated(EnumType.STRING)
  @Column(name = "failure_code", length = 50)
  private FailureCode failureCode;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "next_retry_at")
  private LocalDateTime nextRetryAt;

  @Column(name = "analysis_started_at")
  private LocalDateTime analysisStartedAt;

  @Column(name = "analysis_completed_at")
  private LocalDateTime analysisCompletedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /** Maps the domain model into the persistence entity. */
  public static VerificationRequestEntity from(VerificationRequest domain) {
    return VerificationRequestEntity.builder()
        .id(domain.getId())
        .verificationId(domain.getVerificationId())
        .userId(domain.getUserId())
        .verificationKind(domain.getVerificationKind())
        .appProvider(domain.getAppProvider())
        .status(domain.getStatus())
        .exerciseDate(domain.getExerciseDate())
        .tempObjectKey(domain.getTempObjectKey())
        .tempObjectExpiresAt(domain.getTempObjectExpiresAt())
        .imageContentType(domain.getImageContentType())
        .imageSizeBytes(domain.getImageSizeBytes())
        .imageSha256(domain.getImageSha256())
        .requestFingerprint(domain.getRequestFingerprint())
        .confidenceScore(domain.getConfidenceScore())
        .rejectionReasonCode(domain.getRejectionReasonCode())
        .rejectionReasonDetail(domain.getRejectionReasonDetail())
        .failureCode(domain.getFailureCode())
        .retryCount(domain.getRetryCount())
        .nextRetryAt(domain.getNextRetryAt())
        .analysisStartedAt(domain.getAnalysisStartedAt())
        .analysisCompletedAt(domain.getAnalysisCompletedAt())
        .createdAt(domain.getCreatedAt())
        .updatedAt(domain.getUpdatedAt())
        .build();
  }

  /** Maps the persistence entity into the domain model. */
  public VerificationRequest toDomain() {
    return VerificationRequest.builder()
        .id(id)
        .verificationId(verificationId)
        .userId(userId)
        .verificationKind(verificationKind)
        .appProvider(appProvider)
        .status(status)
        .exerciseDate(exerciseDate)
        .tempObjectKey(tempObjectKey)
        .tempObjectExpiresAt(tempObjectExpiresAt)
        .imageContentType(imageContentType)
        .imageSizeBytes(imageSizeBytes)
        .imageSha256(imageSha256)
        .requestFingerprint(requestFingerprint)
        .confidenceScore(confidenceScore)
        .rejectionReasonCode(rejectionReasonCode)
        .rejectionReasonDetail(rejectionReasonDetail)
        .failureCode(failureCode)
        .retryCount(retryCount)
        .nextRetryAt(nextRetryAt)
        .analysisStartedAt(analysisStartedAt)
        .analysisCompletedAt(analysisCompletedAt)
        .createdAt(createdAt)
        .updatedAt(updatedAt)
        .build();
  }

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = createdAt;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
