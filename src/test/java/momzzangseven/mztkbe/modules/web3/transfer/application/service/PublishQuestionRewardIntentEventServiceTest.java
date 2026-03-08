package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.domain.event.QuestionRewardIntentCanceledEvent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.event.QuestionRewardIntentRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PublishQuestionRewardIntentEventServiceTest {

  @Mock private ApplicationEventPublisher eventPublisher;

  private PublishQuestionRewardIntentEventService service;

  @BeforeEach
  void setUp() {
    service = new PublishQuestionRewardIntentEventService(eventPublisher);
  }

  @Test
  void publishRegisterRequested_shouldEmitRequestedEvent() {
    RegisterQuestionRewardIntentCommand command =
        new RegisterQuestionRewardIntentCommand(101L, 1001L, 1L, 2L, BigInteger.valueOf(100));

    service.publishRegisterRequested(command);

    ArgumentCaptor<QuestionRewardIntentRequestedEvent> captor =
        ArgumentCaptor.forClass(QuestionRewardIntentRequestedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    QuestionRewardIntentRequestedEvent event = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(event.postId()).isEqualTo(101L);
  }

  @Test
  void publishCancelRequested_shouldEmitCanceledEvent() {
    CancelQuestionRewardIntentCommand command = new CancelQuestionRewardIntentCommand(101L, 1001L);

    service.publishCancelRequested(command);

    ArgumentCaptor<QuestionRewardIntentCanceledEvent> captor =
        ArgumentCaptor.forClass(QuestionRewardIntentCanceledEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    QuestionRewardIntentCanceledEvent event = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(event.postId()).isEqualTo(101L);
    org.assertj.core.api.Assertions.assertThat(event.acceptedCommentId()).isEqualTo(1001L);
  }

  @Test
  void publishRegisterRequested_shouldThrow_whenCommandIsNull() {
    assertThatThrownBy(() -> service.publishRegisterRequested(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void publishCancelRequested_shouldThrow_whenCommandIsNull() {
    assertThatThrownBy(() -> service.publishCancelRequested(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }
}
