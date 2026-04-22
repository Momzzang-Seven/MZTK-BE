package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CalculateQnaAdminSettlementReviewQuery validate test")
class CalculateQnaAdminSettlementReviewQueryTest {

  @Test
  @DisplayName("positive postId/answerId면 validate가 성공한다")
  void validate_withPositiveIds_succeeds() {
    assertThatCode(() -> new CalculateQnaAdminSettlementReviewQuery(1L, 2L).validate())
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("invalid postId면 validate가 예외를 던진다")
  void validate_withInvalidPostId_throwsException() {
    assertThatThrownBy(() -> new CalculateQnaAdminSettlementReviewQuery(0L, 2L).validate())
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("postId must be positive");
  }

  @Test
  @DisplayName("invalid answerId면 validate가 예외를 던진다")
  void validate_withInvalidAnswerId_throwsException() {
    assertThatThrownBy(() -> new CalculateQnaAdminSettlementReviewQuery(1L, 0L).validate())
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("answerId must be positive");
  }
}
