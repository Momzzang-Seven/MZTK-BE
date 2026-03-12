package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CancelQuestionRewardIntentServiceTest {

  @Mock private QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  private CancelQuestionRewardIntentService service;

  @BeforeEach
  void setUp() {
    service = new CancelQuestionRewardIntentService(questionRewardIntentPersistencePort);
  }

  @Test
  void execute_shouldCancelWhenCurrentStatusIsPendingLike() {
    QuestionRewardIntent existing =
        QuestionRewardIntent.builder()
            .id(1L)
            .postId(101L)
            .acceptedCommentId(1001L)
            .fromUserId(1L)
            .toUserId(2L)
            .amountWei(BigInteger.valueOf(100))
            .status(QuestionRewardIntentStatus.PREPARE_REQUIRED)
            .build();

    when(questionRewardIntentPersistencePort.findForUpdateByPostId(101L))
        .thenReturn(Optional.of(existing));
    when(questionRewardIntentPersistencePort.update(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(inv -> inv.getArgument(0));

    CancelQuestionRewardIntentResult result =
        service.execute(new CancelQuestionRewardIntentCommand(101L, 1001L));

    assertThat(result.found()).isTrue();
    assertThat(result.changed()).isTrue();
    assertThat(result.status()).isEqualTo(QuestionRewardIntentStatus.CANCELED);
    verify(questionRewardIntentPersistencePort).update(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_shouldIgnoreStaleCancelRequestWhenAcceptedCommentChanged() {
    QuestionRewardIntent existing =
        QuestionRewardIntent.builder()
            .id(1L)
            .postId(101L)
            .acceptedCommentId(2002L)
            .fromUserId(1L)
            .toUserId(2L)
            .amountWei(BigInteger.valueOf(100))
            .status(QuestionRewardIntentStatus.PREPARE_REQUIRED)
            .build();

    when(questionRewardIntentPersistencePort.findForUpdateByPostId(101L))
        .thenReturn(Optional.of(existing));

    CancelQuestionRewardIntentResult result =
        service.execute(new CancelQuestionRewardIntentCommand(101L, 1001L));

    assertThat(result.found()).isTrue();
    assertThat(result.changed()).isFalse();
    assertThat(result.status()).isEqualTo(QuestionRewardIntentStatus.PREPARE_REQUIRED);
    verify(questionRewardIntentPersistencePort, never()).update(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_shouldNotCancelWhenAlreadySucceeded() {
    QuestionRewardIntent existing =
        QuestionRewardIntent.builder()
            .id(1L)
            .postId(101L)
            .acceptedCommentId(1001L)
            .fromUserId(1L)
            .toUserId(2L)
            .amountWei(BigInteger.valueOf(100))
            .status(QuestionRewardIntentStatus.SUCCEEDED)
            .build();

    when(questionRewardIntentPersistencePort.findForUpdateByPostId(101L))
        .thenReturn(Optional.of(existing));

    CancelQuestionRewardIntentResult result =
        service.execute(new CancelQuestionRewardIntentCommand(101L, 1001L));

    assertThat(result.found()).isTrue();
    assertThat(result.changed()).isFalse();
    assertThat(result.status()).isEqualTo(QuestionRewardIntentStatus.SUCCEEDED);
    verify(questionRewardIntentPersistencePort, never()).update(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_shouldReturnNotFound_whenIntentDoesNotExist() {
    when(questionRewardIntentPersistencePort.findForUpdateByPostId(101L))
        .thenReturn(Optional.empty());

    CancelQuestionRewardIntentResult result =
        service.execute(new CancelQuestionRewardIntentCommand(101L, 1001L));

    assertThat(result.postId()).isEqualTo(101L);
    assertThat(result.found()).isFalse();
    assertThat(result.changed()).isFalse();
    assertThat(result.status()).isNull();
  }

  @Test
  void execute_shouldThrow_whenCommandIsNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }
}
