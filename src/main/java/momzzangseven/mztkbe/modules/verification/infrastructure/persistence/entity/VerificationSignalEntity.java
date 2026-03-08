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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationSignal;
import momzzangseven.mztkbe.modules.verification.domain.vo.SignalType;

@Entity
@Table(
    name = "verification_signals",
    indexes = {
      @Index(
          name = "idx_verification_signal_request",
          columnList = "verification_request_id,signal_type")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_verification_signal_unique",
          columnNames = {"verification_request_id", "signal_type", "signal_key"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VerificationSignalEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "verification_request_id", nullable = false)
  private Long verificationRequestId;

  @Enumerated(EnumType.STRING)
  @Column(name = "signal_type", nullable = false, length = 20)
  private SignalType signalType;

  @Column(name = "signal_key", nullable = false, length = 100)
  private String signalKey;

  @Column(name = "signal_value", columnDefinition = "TEXT")
  private String signalValue;

  @Column(name = "confidence", precision = 5, scale = 4)
  private BigDecimal confidence;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  /** Maps the domain model into the persistence entity. */
  public static VerificationSignalEntity from(VerificationSignal domain) {
    return VerificationSignalEntity.builder()
        .id(domain.getId())
        .verificationRequestId(domain.getVerificationRequestId())
        .signalType(domain.getSignalType())
        .signalKey(domain.getSignalKey())
        .signalValue(domain.getSignalValue())
        .confidence(domain.getConfidence())
        .createdAt(domain.getCreatedAt())
        .build();
  }

  /** Maps the persistence entity into the domain model. */
  public VerificationSignal toDomain() {
    return VerificationSignal.builder()
        .id(id)
        .verificationRequestId(verificationRequestId)
        .signalType(signalType)
        .signalKey(signalKey)
        .signalValue(signalValue)
        .confidence(confidence)
        .createdAt(createdAt)
        .build();
  }

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}
