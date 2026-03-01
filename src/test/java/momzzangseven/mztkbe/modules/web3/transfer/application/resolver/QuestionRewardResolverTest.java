package momzzangseven.mztkbe.modules.web3.transfer.application.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.ResolvedReward;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionRewardResolverTest {

  @Mock private QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  private QuestionRewardResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new QuestionRewardResolver(questionRewardIntentPersistencePort);
  }

  @Test
  void supports_onlyQuestionReward() {
    assertThat(resolver.supports(DomainReferenceType.QUESTION_REWARD)).isTrue();
    assertThat(resolver.supports(DomainReferenceType.LEVEL_UP_REWARD)).isFalse();
  }

  @Test
  void resolve_returnsAcceptedAnswerWriterAndRewardWei_whenQuestionRewardSourceIsValid() {
    when(questionRewardIntentPersistencePort.findByPostId(101L))
        .thenReturn(
            Optional.of(
                intent(
                    101L,
                    201L,
                    7L,
                    22L,
                    new BigInteger("5000000000000000000"),
                    QuestionRewardIntentStatus.PREPARE_REQUIRED)));

    ResolvedReward resolved = resolver.resolve(7L, "101");

    assertThat(resolved.toUserId()).isEqualTo(22L);
    assertThat(resolved.amountWei()).isEqualTo(new BigInteger("5000000000000000000"));
  }

  @Test
  void resolve_throws_whenReferenceIdIsNotNumeric() {
    assertThatThrownBy(() -> resolver.resolve(7L, "abc"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("numeric post id");
  }

  @Test
  void resolve_throws_whenIntentNotFound() {
    when(questionRewardIntentPersistencePort.findByPostId(101L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> resolver.resolve(7L, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("question reward intent not found");
  }

  @Test
  void resolve_throws_whenIntentAlreadySubmitted() {
    when(questionRewardIntentPersistencePort.findByPostId(101L))
        .thenReturn(
            Optional.of(
                intent(
                    101L,
                    201L,
                    7L,
                    22L,
                    new BigInteger("5000000000000000000"),
                    QuestionRewardIntentStatus.SUBMITTED)));

    assertThatThrownBy(() -> resolver.resolve(7L, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("already in submitted state");
  }

  @Test
  void resolve_throws_whenIntentAlreadySucceeded() {
    when(questionRewardIntentPersistencePort.findByPostId(101L))
        .thenReturn(
            Optional.of(
                intent(
                    101L,
                    201L,
                    7L,
                    22L,
                    new BigInteger("5000000000000000000"),
                    QuestionRewardIntentStatus.SUCCEEDED)));

    assertThatThrownBy(() -> resolver.resolve(7L, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("already settled");
  }

  @Test
  void resolve_throws_whenRequesterIsNotQuestionOwner() {
    when(questionRewardIntentPersistencePort.findByPostId(101L))
        .thenReturn(
            Optional.of(
                intent(
                    101L,
                    201L,
                    7L,
                    22L,
                    new BigInteger("5000000000000000000"),
                    QuestionRewardIntentStatus.PREPARE_REQUIRED)));

    assertThatThrownBy(() -> resolver.resolve(99L, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("only question owner");
  }

  @Test
  void resolve_throws_whenIntentFailedOnchain() {
    when(questionRewardIntentPersistencePort.findByPostId(101L))
        .thenReturn(
            Optional.of(
                intent(
                    101L,
                    201L,
                    7L,
                    22L,
                    new BigInteger("5000000000000000000"),
                    QuestionRewardIntentStatus.FAILED_ONCHAIN)));

    assertThatThrownBy(() -> resolver.resolve(7L, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("failed onchain");
  }

  @Test
  void resolve_throws_whenIntentCanceled() {
    when(questionRewardIntentPersistencePort.findByPostId(101L))
        .thenReturn(
            Optional.of(
                intent(
                    101L,
                    201L,
                    7L,
                    22L,
                    new BigInteger("5000000000000000000"),
                    QuestionRewardIntentStatus.CANCELED)));

    assertThatThrownBy(() -> resolver.resolve(7L, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("intent is canceled");
  }

  @Test
  void resolve_throws_whenAcceptedCommentIdIsInvalid() {
    when(questionRewardIntentPersistencePort.findByPostId(101L))
        .thenReturn(
            Optional.of(
                intent(
                    101L,
                    null,
                    7L,
                    22L,
                    new BigInteger("5000000000000000000"),
                    QuestionRewardIntentStatus.PREPARE_REQUIRED)));

    assertThatThrownBy(() -> resolver.resolve(7L, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("invalid acceptedCommentId");
  }

  @Test
  void resolve_throws_whenToUserIdIsInvalid() {
    when(questionRewardIntentPersistencePort.findByPostId(101L))
        .thenReturn(
            Optional.of(
                intent(
                    101L,
                    201L,
                    7L,
                    0L,
                    new BigInteger("5000000000000000000"),
                    QuestionRewardIntentStatus.PREPARE_REQUIRED)));

    assertThatThrownBy(() -> resolver.resolve(7L, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("invalid writer userId");
  }

  @Test
  void resolve_throws_whenAmountWeiIsInvalid() {
    when(questionRewardIntentPersistencePort.findByPostId(101L))
        .thenReturn(
            Optional.of(
                intent(
                    101L,
                    201L,
                    7L,
                    22L,
                    BigInteger.ZERO,
                    QuestionRewardIntentStatus.PREPARE_REQUIRED)));

    assertThatThrownBy(() -> resolver.resolve(7L, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("invalid amountWei");
  }

  private QuestionRewardIntent intent(
      Long postId,
      Long acceptedCommentId,
      Long fromUserId,
      Long toUserId,
      BigInteger amountWei,
      QuestionRewardIntentStatus status) {
    return QuestionRewardIntent.builder()
        .postId(postId)
        .acceptedCommentId(acceptedCommentId)
        .fromUserId(fromUserId)
        .toUserId(toUserId)
        .amountWei(amountWei)
        .status(status)
        .build();
  }
}
