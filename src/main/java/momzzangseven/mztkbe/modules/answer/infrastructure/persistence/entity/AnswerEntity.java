package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerDeleteStatus;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerPublicationStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AnswerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "post_id", nullable = false)
  private Long postId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  @Column(name = "is_accepted", nullable = false)
  private Boolean isAccepted = false;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "publication_status",
      nullable = false,
      length = 32,
      columnDefinition = "varchar(32) default 'VISIBLE'")
  private AnswerPublicationStatus publicationStatus = AnswerPublicationStatus.VISIBLE;

  @Column(name = "current_create_execution_intent_id", length = 100)
  private String currentCreateExecutionIntentId;

  @Column(name = "create_preparation_token", length = 100)
  private String createPreparationToken;

  @Column(name = "publication_failure_terminal_status", length = 40)
  private String publicationFailureTerminalStatus;

  @Column(name = "publication_failure_reason", length = 500)
  private String publicationFailureReason;

  @Column(name = "create_preparation_expires_at")
  private LocalDateTime createPreparationExpiresAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "pending_delete_status", length = 32)
  private AnswerDeleteStatus pendingDeleteStatus;

  @Column(name = "current_delete_execution_intent_id", length = 100)
  private String currentDeleteExecutionIntentId;

  @Column(name = "delete_preparation_token", length = 100)
  private String deletePreparationToken;

  @Column(name = "delete_preparation_expires_at")
  private LocalDateTime deletePreparationExpiresAt;

  @Column(name = "delete_failure_terminal_status", length = 40)
  private String deleteFailureTerminalStatus;

  @Column(name = "delete_failure_reason", length = 500)
  private String deleteFailureReason;

  @Column(name = "reconciliation_required_reason", length = 500)
  private String reconciliationRequiredReason;

  @Column(name = "reconciliation_required_intent_id", length = 100)
  private String reconciliationRequiredIntentId;

  @CreatedDate
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public AnswerEntity(
      Long id,
      Long postId,
      Long userId,
      String content,
      Boolean isAccepted,
      AnswerPublicationStatus publicationStatus,
      String currentCreateExecutionIntentId,
      String createPreparationToken,
      String publicationFailureTerminalStatus,
      String publicationFailureReason,
      LocalDateTime createPreparationExpiresAt,
      AnswerDeleteStatus pendingDeleteStatus,
      String currentDeleteExecutionIntentId,
      String deletePreparationToken,
      LocalDateTime deletePreparationExpiresAt,
      String deleteFailureTerminalStatus,
      String deleteFailureReason,
      String reconciliationRequiredReason,
      String reconciliationRequiredIntentId) {
    this.id = id;
    this.postId = postId;
    this.userId = userId;
    this.content = content;
    this.isAccepted = isAccepted != null ? isAccepted : false;
    this.publicationStatus =
        publicationStatus == null ? AnswerPublicationStatus.VISIBLE : publicationStatus;
    this.currentCreateExecutionIntentId = currentCreateExecutionIntentId;
    this.createPreparationToken = createPreparationToken;
    this.publicationFailureTerminalStatus = publicationFailureTerminalStatus;
    this.publicationFailureReason = publicationFailureReason;
    this.createPreparationExpiresAt = createPreparationExpiresAt;
    this.pendingDeleteStatus = pendingDeleteStatus;
    this.currentDeleteExecutionIntentId = currentDeleteExecutionIntentId;
    this.deletePreparationToken = deletePreparationToken;
    this.deletePreparationExpiresAt = deletePreparationExpiresAt;
    this.deleteFailureTerminalStatus = deleteFailureTerminalStatus;
    this.deleteFailureReason = deleteFailureReason;
    this.reconciliationRequiredReason = reconciliationRequiredReason;
    this.reconciliationRequiredIntentId = reconciliationRequiredIntentId;
  }
}
