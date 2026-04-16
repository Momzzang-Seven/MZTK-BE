package momzzangseven.mztkbe.modules.answer.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateAnswerResponse unit test")
class CreateAnswerResponseTest {

  @Test
  @DisplayName("from(CreateAnswerResult) maps ids")
  void from_mapsIds() {
    CreateAnswerResult result = new CreateAnswerResult(11L, 77L, null);

    CreateAnswerResponse response = CreateAnswerResponse.from(result);

    assertThat(response.postId()).isEqualTo(11L);
    assertThat(response.answerId()).isEqualTo(77L);
  }
}
