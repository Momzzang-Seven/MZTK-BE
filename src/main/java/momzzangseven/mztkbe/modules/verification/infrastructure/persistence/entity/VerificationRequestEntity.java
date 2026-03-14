package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationRewardStatus;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "verification_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VerificationRequestEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "verification_id", nullable = false, unique = true, length = 36)
  private String verificationId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "verification_kind", nullable = false, length = 30)
  private String verificationKind;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "exercise_date")
  private LocalDate exerciseDate;

  @Column(name = "shot_at_kst")
  private LocalDateTime shotAtKst;

  @Column(name = "tmp_object_key", nullable = false, unique = true, length = 512)
  private String tmpObjectKey;

  @Builder.Default
  @Column(name = "reward_status", nullable = false, length = 20)
  private String rewardStatus = VerificationRewardStatus.NOT_REQUESTED.name();

  @Column(name = "reward_source_ref", length = 255)
  private String rewardSourceRef;

  @Column(name = "rejection_reason_code", length = 50)
  private String rejectionReasonCode;

  @Column(name = "rejection_reason_detail", length = 500)
  private String rejectionReasonDetail;

  @Column(name = "failure_code", length = 50)
  private String failureCode;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static VerificationRequestEntity from(VerificationRequest request) {
    return VerificationRequestEntity.builder()
        .id(request.getId())
        .verificationId(request.getVerificationId())
        .userId(request.getUserId())
        .verificationKind(request.getVerificationKind().name())
        .status(request.getStatus().name())
        .exerciseDate(request.getExerciseDate())
        .shotAtKst(request.getShotAtKst())
        .tmpObjectKey(request.getTmpObjectKey())
        .rewardStatus(request.getRewardStatus().name())
        .rewardSourceRef(request.getRewardSourceRef())
        .rejectionReasonCode(
            request.getRejectionReasonCode() == null
                ? null
                : request.getRejectionReasonCode().name())
        .rejectionReasonDetail(request.getRejectionReasonDetail())
        .failureCode(request.getFailureCode() == null ? null : request.getFailureCode().name())
        .createdAt(request.getCreatedAt())
        .updatedAt(request.getUpdatedAt())
        .build();
  }

  public VerificationRequest toDomain() {
    return VerificationRequest.builder()
        .id(id)
        .verificationId(verificationId)
        .userId(userId)
        .verificationKind(VerificationKind.valueOf(verificationKind))
        .status(VerificationStatus.valueOf(status))
        .exerciseDate(exerciseDate)
        .shotAtKst(shotAtKst)
        .tmpObjectKey(tmpObjectKey)
        .rewardStatus(
            rewardStatus == null
                ? VerificationRewardStatus.NOT_REQUESTED
                : VerificationRewardStatus.valueOf(rewardStatus))
        .rewardSourceRef(rewardSourceRef)
        .rejectionReasonCode(
            rejectionReasonCode == null ? null : RejectionReasonCode.valueOf(rejectionReasonCode))
        .rejectionReasonDetail(rejectionReasonDetail)
        .failureCode(failureCode == null ? null : FailureCode.valueOf(failureCode))
        .createdAt(createdAt)
        .updatedAt(updatedAt)
        .build();
  }
}
