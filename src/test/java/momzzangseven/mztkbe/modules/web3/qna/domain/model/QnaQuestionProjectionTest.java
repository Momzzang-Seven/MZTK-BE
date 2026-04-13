package momzzangseven.mztkbe.modules.web3.qna.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("QnaQuestionProjection 도메인 모델 단위 테스트")
class QnaQuestionProjectionTest {

  private static final Long POST_ID = 101L;
  private static final Long ASKER_USER_ID = 7L;
  private static final String QUESTION_ID = QnaEscrowIdCodec.questionId(POST_ID);
  private static final String TOKEN_ADDRESS = "0x" + "2".repeat(40);
  private static final BigInteger REWARD_AMOUNT_WEI = new BigInteger("50000000000000000000");
  private static final String QUESTION_HASH = "0x" + "a".repeat(64);

  private QnaQuestionProjection createDefault() {
    return QnaQuestionProjection.create(
        POST_ID, ASKER_USER_ID, QUESTION_ID, TOKEN_ADDRESS, REWARD_AMOUNT_WEI, QUESTION_HASH);
  }

  @Nested
  @DisplayName("create() — 성공 케이스")
  class CreateSuccessCases {

    @Test
    @DisplayName("유효한 값으로 생성 시 CREATED 상태, answerCount=0 인 projection 이 반환된다")
    void create_returnsCreatedState() {
      QnaQuestionProjection projection = createDefault();

      assertThat(projection.getState()).isEqualTo(QnaQuestionState.CREATED);
      assertThat(projection.getAnswerCount()).isZero();
      assertThat(projection.getAcceptedAnswerId()).isEqualTo(QnaEscrowIdCodec.zeroBytes32());
    }
  }

  @Nested
  @DisplayName("syncAnswerCount()")
  class SyncAnswerCountCases {

    @Test
    @DisplayName("answerCount 가 0 → 1 로 증가하면 ANSWERED 상태로 전이된다")
    void syncAnswerCount_toOne_becomesAnswered() {
      QnaQuestionProjection updated = createDefault().syncAnswerCount(1);

      assertThat(updated.getAnswerCount()).isEqualTo(1);
      assertThat(updated.getState()).isEqualTo(QnaQuestionState.ANSWERED);
    }

    @Test
    @DisplayName("answerCount 가 1 → 0 으로 감소하면 CREATED 상태로 복귀한다")
    void syncAnswerCount_toZero_becomesCreated() {
      QnaQuestionProjection withAnswer = createDefault().syncAnswerCount(1);
      QnaQuestionProjection updated = withAnswer.syncAnswerCount(0);

      assertThat(updated.getAnswerCount()).isZero();
      assertThat(updated.getState()).isEqualTo(QnaQuestionState.CREATED);
    }

    @Test
    @DisplayName("음수 answerCount 시 예외를 던진다")
    void syncAnswerCount_negative_throwsException() {
      assertThatThrownBy(() -> createDefault().syncAnswerCount(-1))
          .isInstanceOf(Web3InvalidInputException.class);
    }
  }

  @Nested
  @DisplayName("markAccepted()")
  class MarkAcceptedCases {

    @Test
    @DisplayName("markAccepted 호출 시 PAID_OUT 상태로 전이되고 acceptedAnswerId 가 설정된다")
    void markAccepted_becomesPaidOut() {
      String answerKey = QnaEscrowIdCodec.answerId(201L);
      QnaQuestionProjection accepted = createDefault().syncAnswerCount(1).markAccepted(answerKey);

      assertThat(accepted.getState()).isEqualTo(QnaQuestionState.PAID_OUT);
      assertThat(accepted.getAcceptedAnswerId()).isEqualTo(answerKey);
    }

    @Test
    @DisplayName("null acceptedAnswerId 시 예외를 던진다")
    void markAccepted_null_throwsException() {
      assertThatThrownBy(() -> createDefault().markAccepted(null))
          .isInstanceOf(Web3InvalidInputException.class);
    }
  }

  @Nested
  @DisplayName("markDeleted()")
  class MarkDeletedCases {

    @Test
    @DisplayName("markDeleted 호출 시 DELETED 상태로 전이된다")
    void markDeleted_becomesDeleted() {
      QnaQuestionProjection deleted = createDefault().markDeleted();

      assertThat(deleted.getState()).isEqualTo(QnaQuestionState.DELETED);
    }
  }
}
