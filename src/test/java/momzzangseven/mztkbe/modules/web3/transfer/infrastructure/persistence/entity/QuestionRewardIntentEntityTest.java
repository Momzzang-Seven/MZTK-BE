package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import org.junit.jupiter.api.Test;

class QuestionRewardIntentEntityTest {

  @Test
  void onCreate_setsDefaultStatus() {
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
  }

  @Test
  void onCreate_keepsExplicitStatus() {
    QuestionRewardIntentEntity entity =
        QuestionRewardIntentEntity.builder()
            .postId(101L)
            .acceptedCommentId(201L)
            .fromUserId(7L)
            .toUserId(22L)
            .amountWei(BigInteger.TEN)
            .status(QuestionRewardIntentStatus.SUBMITTED)
            .build();

    entity.onCreate();

    assertThat(entity.getStatus()).isEqualTo(QuestionRewardIntentStatus.SUBMITTED);
  }
}
