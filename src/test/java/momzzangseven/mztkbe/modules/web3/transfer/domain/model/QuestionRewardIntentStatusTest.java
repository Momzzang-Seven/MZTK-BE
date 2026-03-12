package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QuestionRewardIntentStatus unit test")
class QuestionRewardIntentStatusTest {

  @Test
  @DisplayName("isFinalized returns true only for SUCCEEDED and CANCELED")
  void isFinalized_returnsExpectedValue() {
    assertThat(QuestionRewardIntentStatus.SUCCEEDED.isFinalized()).isTrue();
    assertThat(QuestionRewardIntentStatus.CANCELED.isFinalized()).isTrue();
    assertThat(QuestionRewardIntentStatus.PREPARE_REQUIRED.isFinalized()).isFalse();
    assertThat(QuestionRewardIntentStatus.SUBMITTED.isFinalized()).isFalse();
    assertThat(QuestionRewardIntentStatus.FAILED_ONCHAIN.isFinalized()).isFalse();
  }

  @Test
  @DisplayName("valueOf rejects invalid name")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> QuestionRewardIntentStatus.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
