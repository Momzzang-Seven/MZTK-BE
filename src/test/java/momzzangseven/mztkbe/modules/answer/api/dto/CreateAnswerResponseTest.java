package momzzangseven.mztkbe.modules.answer.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateAnswerResponse unit test")
class CreateAnswerResponseTest {

  @Test
  @DisplayName("from(CreateAnswerResult) maps answerId")
  void from_mapsAnswerId() {
    CreateAnswerResult result = new CreateAnswerResult(77L);

    CreateAnswerResponse response = CreateAnswerResponse.from(result);

    assertThat(response.answerId()).isEqualTo(77L);
  }
}
