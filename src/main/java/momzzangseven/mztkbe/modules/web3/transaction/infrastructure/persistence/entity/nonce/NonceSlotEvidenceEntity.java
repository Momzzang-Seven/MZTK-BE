package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceEvidenceSource;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceEvidenceType;

@Entity
@Table(
    name = "web3_nonce_slot_evidence",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_web3_nonce_slot_evidence_scope_id",
          columnNames = {"chain_id", "from_address", "nonce", "id"})
    },
    indexes = {
      @Index(
          name = "idx_web3_nonce_slot_evidence_scope_type_observed",
          columnList = "chain_id,from_address,nonce,evidence_type,observed_at")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NonceSlotEvidenceEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "chain_id", nullable = false)
  private Long chainId;

  @Column(name = "from_address", nullable = false, length = 42)
  private String fromAddress;

  @Column(name = "nonce", nullable = false)
  private Long nonce;

  @Enumerated(EnumType.STRING)
  @Column(name = "evidence_type", nullable = false, length = 60)
  private SponsorNonceEvidenceType evidenceType;

  @Enumerated(EnumType.STRING)
  @Column(name = "evidence_source", nullable = false, length = 20)
  private SponsorNonceEvidenceSource evidenceSource;

  @Column(name = "provider_alias", length = 40)
  private String providerAlias;

  @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
  private String payloadJson;

  @Column(name = "related_evidence_id")
  private Long relatedEvidenceId;

  @Column(name = "created_by", length = 120)
  private String createdBy;

  @Column(name = "observed_at", nullable = false)
  private LocalDateTime observedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (observedAt == null) {
      observedAt = now;
    }
    if (createdAt == null) {
      createdAt = now;
    }
  }
}
