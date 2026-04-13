package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "web3_qna_answers",
    indexes = {
      @Index(name = "uk_web3_qna_answers_answer_key", columnList = "answer_key", unique = true),
      @Index(name = "idx_web3_qna_answers_post_id", columnList = "post_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class QnaAnswerProjectionEntity {

  @Id
  @Column(name = "answer_id", nullable = false)
  private Long answerId;

  @Column(name = "post_id", nullable = false)
  private Long postId;

  @Column(name = "question_id", nullable = false, length = 66)
  private String questionId;

  @Column(name = "answer_key", nullable = false, length = 66)
  private String answerKey;

  @Column(name = "responder_user_id", nullable = false)
  private Long responderUserId;

  @Column(name = "content_hash", nullable = false, length = 66)
  private String contentHash;

  @Column(name = "accepted", nullable = false)
  private boolean accepted;

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
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
