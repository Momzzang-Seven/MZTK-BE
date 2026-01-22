package momzzangseven.mztkbe.modules.web3.challenge.infrastructure.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengePurpose;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengeStatus;

@Entity
@Table(name = "challenges")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeEntity {

  @Id
  @Column(name = "nonce", length = 36, nullable = false)
  private String nonce;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", length = 50, nullable = false)
  private ChallengePurpose purpose;

  @Column(name = "wallet_address", length = 42, nullable = false)
  private String walletAddress;

  @Column(name = "message", columnDefinition = "TEXT", nullable = false)
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 20, nullable = false)
  private ChallengeStatus status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @PrePersist
  protected void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
  }
}
