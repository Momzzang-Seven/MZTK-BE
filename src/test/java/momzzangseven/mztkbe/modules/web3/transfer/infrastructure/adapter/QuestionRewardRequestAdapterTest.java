package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.PublishQuestionRewardIntentEventUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.TransferRewardTokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionRewardRequestAdapterTest {

  @Mock private PublishQuestionRewardIntentEventUseCase publishQuestionRewardIntentEventUseCase;

  private QuestionRewardRequestAdapter adapter;

  @BeforeEach
  void setUp() {
    TransferRewardTokenProperties properties = new TransferRewardTokenProperties();
    properties.setDecimals(18);
    adapter = new QuestionRewardRequestAdapter(publishQuestionRewardIntentEventUseCase, properties);
  }

  @Test
  void request_publishesWeiConvertedCommand() {
    adapter.request(10L, 20L, 1L, 2L, 100L);

    ArgumentCaptor<RegisterQuestionRewardIntentCommand> captor =
        ArgumentCaptor.forClass(RegisterQuestionRewardIntentCommand.class);
    verify(publishQuestionRewardIntentEventUseCase).publishRegisterRequested(captor.capture());
    RegisterQuestionRewardIntentCommand command = captor.getValue();

    assertThat(command.postId()).isEqualTo(10L);
    assertThat(command.acceptedCommentId()).isEqualTo(20L);
    assertThat(command.fromUserId()).isEqualTo(1L);
    assertThat(command.toUserId()).isEqualTo(2L);
    assertThat(command.amountWei()).isEqualByComparingTo(new BigInteger("100000000000000000000"));
  }

  @Test
  void request_throwsWhenRewardIsInvalid() {
    assertThatThrownBy(() -> adapter.request(10L, 20L, 1L, 2L, 0L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("rewardMztk");
  }
}
