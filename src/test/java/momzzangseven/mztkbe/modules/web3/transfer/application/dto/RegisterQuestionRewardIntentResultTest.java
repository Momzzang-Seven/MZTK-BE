package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import org.junit.jupiter.api.Test;

class RegisterQuestionRewardIntentResultTest {

  @Test
  void record_storesFields() {
    RegisterQuestionRewardIntentResult result =
        new RegisterQuestionRewardIntentResult(
            10L, QuestionRewardIntentStatus.PREPARE_REQUIRED, true);

    assertThat(result.postId()).isEqualTo(10L);
    assertThat(result.status()).isEqualTo(QuestionRewardIntentStatus.PREPARE_REQUIRED);
    assertThat(result.created()).isTrue();
  }
}
