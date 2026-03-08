package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import org.junit.jupiter.api.Test;

class CancelQuestionRewardIntentResultTest {

  @Test
  void record_storesAllValues() {
    CancelQuestionRewardIntentResult result =
        new CancelQuestionRewardIntentResult(101L, QuestionRewardIntentStatus.CANCELED, true, true);

    assertThat(result.postId()).isEqualTo(101L);
    assertThat(result.status()).isEqualTo(QuestionRewardIntentStatus.CANCELED);
    assertThat(result.found()).isTrue();
    assertThat(result.changed()).isTrue();
  }
}
