package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionUpdateStateStatus;

@Entity
@Table(
    name = "qna_question_update_states",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_qna_question_update_states_post_version",
          columnNames = {"post_id", "update_version"}),
      @UniqueConstraint(name = "uk_qna_question_update_states_token", columnNames = "update_token")
    },
    indexes = {
      @Index(
          name = "idx_qna_question_update_states_post_latest",
          columnList = "post_id, update_version DESC"),
      @Index(
          name = "idx_qna_question_update_states_status_updated_at",
          columnList = "status, updated_at")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class QnaQuestionUpdateStateEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "post_id", nullable = false)
  private Long postId;

  @Column(name = "requester_user_id", nullable = false)
  private Long requesterUserId;

  @Column(name = "update_version", nullable = false)
  private Long updateVersion;

  @Column(name = "update_token", nullable = false, length = 64)
  private String updateToken;

  @Column(name = "expected_question_hash", nullable = false, length = 66)
  private String expectedQuestionHash;

  @Column(name = "execution_intent_public_id", length = 36)
  private String executionIntentPublicId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private QnaQuestionUpdateStateStatus status;

  @Builder.Default
  @Column(name = "preparation_retryable", nullable = false)
  private boolean preparationRetryable = true;

  @Column(name = "last_error_code", length = 120)
  private String lastErrorCode;

  @Column(name = "last_error_reason", columnDefinition = "TEXT")
  private String lastErrorReason;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
