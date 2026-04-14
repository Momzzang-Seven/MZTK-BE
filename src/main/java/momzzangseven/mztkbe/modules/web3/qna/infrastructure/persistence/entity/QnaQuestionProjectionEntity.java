package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "web3_qna_questions",
    indexes = {
      @Index(name = "uk_web3_qna_questions_question_id", columnList = "question_id", unique = true),
      @Index(name = "idx_web3_qna_questions_state", columnList = "state")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class QnaQuestionProjectionEntity {

  @Id
  @Column(name = "post_id", nullable = false)
  private Long postId;

  @Column(name = "question_id", nullable = false, length = 66)
  private String questionId;

  @Column(name = "asker_user_id", nullable = false)
  private Long askerUserId;

  @Column(name = "token_address", nullable = false, length = 42)
  private String tokenAddress;

  @Column(name = "reward_amount_wei", nullable = false, precision = 78, scale = 0)
  private BigInteger rewardAmountWei;

  @Column(name = "question_hash", nullable = false, length = 66)
  private String questionHash;

  @Column(name = "accepted_answer_id", nullable = false, length = 66)
  private String acceptedAnswerId;

  @Column(name = "answer_count", nullable = false)
  private Integer answerCount;

  @Column(name = "state", nullable = false)
  private Integer state;

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
