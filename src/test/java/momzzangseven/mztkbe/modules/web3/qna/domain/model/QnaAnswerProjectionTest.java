package momzzangseven.mztkbe.modules.web3.qna.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("QnaAnswerProjection 도메인 모델 단위 테스트")
class QnaAnswerProjectionTest {

  private static final Long ANSWER_ID = 201L;
  private static final Long POST_ID = 101L;
  private static final String QUESTION_ID = "0x" + "a".repeat(64);
  private static final String ANSWER_KEY = "0x" + "b".repeat(64);
  private static final Long RESPONDER_USER_ID = 22L;
  private static final String CONTENT_HASH = "0x" + "c".repeat(64);

  @Nested
  @DisplayName("create() — 성공 케이스")
  class CreateSuccessCases {

    @Test
    @DisplayName("유효한 값으로 생성 시 accepted=false 인 projection 이 반환된다")
    void create_returnsProjectionWithAcceptedFalse() {
      QnaAnswerProjection projection =
          QnaAnswerProjection.create(
              ANSWER_ID, POST_ID, QUESTION_ID, ANSWER_KEY, RESPONDER_USER_ID, CONTENT_HASH);

      assertThat(projection.getAnswerId()).isEqualTo(ANSWER_ID);
      assertThat(projection.getPostId()).isEqualTo(POST_ID);
      assertThat(projection.getQuestionId()).isEqualTo(QUESTION_ID);
      assertThat(projection.getAnswerKey()).isEqualTo(ANSWER_KEY);
      assertThat(projection.getResponderUserId()).isEqualTo(RESPONDER_USER_ID);
      assertThat(projection.getContentHash()).isEqualTo(CONTENT_HASH);
      assertThat(projection.isAccepted()).isFalse();
    }
  }

  @Nested
  @DisplayName("create() — 실패 케이스")
  class CreateFailureCases {

    @Test
    @DisplayName("answerId 가 null 이면 예외를 던진다")
    void create_throwsException_whenAnswerIdIsNull() {
      assertThatThrownBy(
              () ->
                  QnaAnswerProjection.create(
                      null, POST_ID, QUESTION_ID, ANSWER_KEY, RESPONDER_USER_ID, CONTENT_HASH))
          .isInstanceOf(Web3InvalidInputException.class);
    }

    @Test
    @DisplayName("contentHash 가 blank 이면 예외를 던진다")
    void create_throwsException_whenContentHashIsBlank() {
      assertThatThrownBy(
              () ->
                  QnaAnswerProjection.create(
                      ANSWER_ID, POST_ID, QUESTION_ID, ANSWER_KEY, RESPONDER_USER_ID, ""))
          .isInstanceOf(Web3InvalidInputException.class);
    }
  }

  @Nested
  @DisplayName("markAccepted()")
  class MarkAcceptedCases {

    @Test
    @DisplayName("markAccepted 호출 시 accepted=true 이고 나머지 필드는 변경되지 않는다")
    void markAccepted_setsAcceptedTrue() {
      QnaAnswerProjection original =
          QnaAnswerProjection.create(
              ANSWER_ID, POST_ID, QUESTION_ID, ANSWER_KEY, RESPONDER_USER_ID, CONTENT_HASH);

      QnaAnswerProjection accepted = original.markAccepted();

      assertThat(accepted.isAccepted()).isTrue();
      assertThat(accepted.getAnswerId()).isEqualTo(ANSWER_ID);
      assertThat(accepted.getContentHash()).isEqualTo(CONTENT_HASH);
      assertThat(accepted.getResponderUserId()).isEqualTo(RESPONDER_USER_ID);
    }
  }

  @Nested
  @DisplayName("updateContentHash()")
  class UpdateContentHashCases {

    @Test
    @DisplayName("유효한 해시로 updateContentHash 호출 시 새 해시가 반영된다")
    void updateContentHash_returnsUpdatedHash() {
      QnaAnswerProjection original =
          QnaAnswerProjection.create(
              ANSWER_ID, POST_ID, QUESTION_ID, ANSWER_KEY, RESPONDER_USER_ID, CONTENT_HASH);
      String newHash = "0x" + "d".repeat(64);

      QnaAnswerProjection updated = original.updateContentHash(newHash);

      assertThat(updated.getContentHash()).isEqualTo(newHash);
      assertThat(updated.getAnswerId()).isEqualTo(ANSWER_ID);
    }

    @Test
    @DisplayName("null 해시로 updateContentHash 호출 시 예외를 던진다")
    void updateContentHash_throwsException_whenNull() {
      QnaAnswerProjection original =
          QnaAnswerProjection.create(
              ANSWER_ID, POST_ID, QUESTION_ID, ANSWER_KEY, RESPONDER_USER_ID, CONTENT_HASH);

      assertThatThrownBy(() -> original.updateContentHash(null))
          .isInstanceOf(Web3InvalidInputException.class);
    }
  }
}
