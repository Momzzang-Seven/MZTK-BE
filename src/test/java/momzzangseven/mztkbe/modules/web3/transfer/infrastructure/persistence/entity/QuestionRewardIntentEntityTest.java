package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import org.junit.jupiter.api.Test;

class QuestionRewardIntentEntityTest {

  @Test
  void onCreate_setsDefaultsAndTimestamps() {
    QuestionRewardIntentEntity entity =
        QuestionRewardIntentEntity.builder()
            .postId(101L)
            .acceptedCommentId(201L)
            .fromUserId(7L)
            .toUserId(22L)
            .amountWei(BigInteger.TEN)
            .build();

    entity.onCreate();

    assertThat(entity.getStatus()).isEqualTo(QuestionRewardIntentStatus.PREPARE_REQUIRED);
    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isNotNull();
  }

  @Test
  void onUpdate_updatesUpdatedAt() {
    QuestionRewardIntentEntity entity =
        QuestionRewardIntentEntity.builder()
            .postId(101L)
            .acceptedCommentId(201L)
            .fromUserId(7L)
            .toUserId(22L)
            .amountWei(BigInteger.TEN)
            .status(QuestionRewardIntentStatus.SUBMITTED)
            .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
            .build();

    entity.onUpdate();

    assertThat(entity.getUpdatedAt()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
  }
}
