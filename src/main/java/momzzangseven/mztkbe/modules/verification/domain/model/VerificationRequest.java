package momzzangseven.mztkbe.modules.verification.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.verification.domain.vo.AppProvider;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;

/** Domain model representing a single workout verification request. */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class VerificationRequest {
  private Long id;
  private String verificationId;
  private Long userId;
  private VerificationKind verificationKind;
  private AppProvider appProvider;
  private VerificationStatus status;
  private LocalDate exerciseDate;
  private String tempObjectKey;
  private LocalDateTime tempObjectExpiresAt;
  private String imageContentType;
  private Long imageSizeBytes;
  private String imageSha256;
  private String requestFingerprint;
  private BigDecimal confidenceScore;
  private RejectionReasonCode rejectionReasonCode;
  private String rejectionReasonDetail;
  private FailureCode failureCode;
  private int retryCount;
  private LocalDateTime nextRetryAt;
  private LocalDateTime analysisStartedAt;
  private LocalDateTime analysisCompletedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public boolean isActive() {
    return status != null && status.isActive();
  }

  public boolean isTerminal() {
    return status != null && status.isTerminal();
  }
}
