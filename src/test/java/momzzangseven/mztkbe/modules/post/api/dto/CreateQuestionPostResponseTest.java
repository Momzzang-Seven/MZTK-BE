package momzzangseven.mztkbe.modules.post.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.post.application.dto.CreateQuestionPostResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateQuestionPostResponse unit test")
class CreateQuestionPostResponseTest {

  @Test
  @DisplayName("from(CreateQuestionPostResult) preserves xp fields and nullable web3")
  void from_mapsFields() {
    CreateQuestionPostResponse response =
        CreateQuestionPostResponse.from(
            new CreateQuestionPostResult(10L, true, 20L, "게시글 작성 완료! (+20 XP)", null));

    assertThat(response.postId()).isEqualTo(10L);
    assertThat(response.isXpGranted()).isTrue();
    assertThat(response.grantedXp()).isEqualTo(20L);
    assertThat(response.message()).contains("+20 XP");
    assertThat(response.web3()).isNull();
  }
}
