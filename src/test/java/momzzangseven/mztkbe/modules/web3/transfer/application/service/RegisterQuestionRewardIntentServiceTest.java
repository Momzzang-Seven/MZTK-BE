package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterQuestionRewardIntentServiceTest {

  @Mock private QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  private RegisterQuestionRewardIntentService service;

  @BeforeEach
  void setUp() {
    service = new RegisterQuestionRewardIntentService(questionRewardIntentPersistencePort);
  }

  @Test
  void execute_createsIntent_whenNotExists() {
    RegisterQuestionRewardIntentCommand command = command();
    when(questionRewardIntentPersistencePort.findForUpdateByPostId(101L))
        .thenReturn(Optional.empty());
    when(questionRewardIntentPersistencePort.create(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(inv -> inv.getArgument(0));

    RegisterQuestionRewardIntentResult result = service.execute(command);

    assertThat(result.postId()).isEqualTo(101L);
    assertThat(result.status()).isEqualTo(QuestionRewardIntentStatus.PREPARE_REQUIRED);
    assertThat(result.created()).isTrue();
  }

  @Test
  void execute_updatesIntent_whenExistingIsMutableStatus() {
    RegisterQuestionRewardIntentCommand command = command();
    QuestionRewardIntent existing =
        QuestionRewardIntent.builder()
            .id(1L)
            .postId(101L)
            .acceptedCommentId(150L)
            .fromUserId(7L)
            .toUserId(8L)
            .amountWei(new BigInteger("1000000000000000000"))
            .status(QuestionRewardIntentStatus.FAILED_ONCHAIN)
            .build();
    when(questionRewardIntentPersistencePort.findForUpdateByPostId(101L))
        .thenReturn(Optional.of(existing));
    when(questionRewardIntentPersistencePort.update(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(inv -> inv.getArgument(0));

    RegisterQuestionRewardIntentResult result = service.execute(command);

    assertThat(result.postId()).isEqualTo(101L);
    assertThat(result.status()).isEqualTo(QuestionRewardIntentStatus.PREPARE_REQUIRED);
    assertThat(result.created()).isFalse();
  }

  @Test
  void execute_returnsExisting_whenSubmittedAndPayloadSame() {
    RegisterQuestionRewardIntentCommand command = command();
    QuestionRewardIntent existing =
        QuestionRewardIntent.builder()
            .id(1L)
            .postId(101L)
            .acceptedCommentId(201L)
            .fromUserId(7L)
            .toUserId(22L)
            .amountWei(new BigInteger("5000000000000000000"))
            .status(QuestionRewardIntentStatus.SUBMITTED)
            .build();
    when(questionRewardIntentPersistencePort.findForUpdateByPostId(101L))
        .thenReturn(Optional.of(existing));

    RegisterQuestionRewardIntentResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(QuestionRewardIntentStatus.SUBMITTED);
    assertThat(result.created()).isFalse();
    verify(questionRewardIntentPersistencePort).findForUpdateByPostId(101L);
  }

  @Test
  void execute_throws_whenSubmittedAndPayloadChanged() {
    RegisterQuestionRewardIntentCommand command = command();
    QuestionRewardIntent existing =
        QuestionRewardIntent.builder()
            .id(1L)
            .postId(101L)
            .acceptedCommentId(999L)
            .fromUserId(7L)
            .toUserId(22L)
            .amountWei(new BigInteger("5000000000000000000"))
            .status(QuestionRewardIntentStatus.SUBMITTED)
            .build();
    when(questionRewardIntentPersistencePort.findForUpdateByPostId(101L))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("cannot be changed");
  }

  @Test
  void execute_throws_whenCommandIsNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  private RegisterQuestionRewardIntentCommand command() {
    return new RegisterQuestionRewardIntentCommand(
        101L, 201L, 7L, 22L, new BigInteger("5000000000000000000"));
  }
}
