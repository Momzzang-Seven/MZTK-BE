package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CalculateQnaAdminRefundReviewQuery validate test")
class CalculateQnaAdminRefundReviewQueryTest {

  @Test
  @DisplayName("positive postId면 validate가 성공한다")
  void validate_withPositivePostId_succeeds() {
    assertThatCode(() -> new CalculateQnaAdminRefundReviewQuery(1L).validate())
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("invalid postId면 validate가 예외를 던진다")
  void validate_withInvalidPostId_throwsException() {
    assertThatThrownBy(() -> new CalculateQnaAdminRefundReviewQuery(0L).validate())
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("postId must be positive");
  }
}
