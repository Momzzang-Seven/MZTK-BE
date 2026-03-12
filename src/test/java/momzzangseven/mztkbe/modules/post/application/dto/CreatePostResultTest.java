package momzzangseven.mztkbe.modules.post.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreatePostResult unit test")
class CreatePostResultTest {

  @Test
  @DisplayName("record fields are exposed")
  void exposesFields() {
    CreatePostResult result = new CreatePostResult(10L, true, 30L, "done");

    assertThat(result.postId()).isEqualTo(10L);
    assertThat(result.isXpGranted()).isTrue();
    assertThat(result.grantedXp()).isEqualTo(30L);
    assertThat(result.message()).isEqualTo("done");
  }

  @Test
  @DisplayName("equality is based on record state")
  void recordEquality() {
    CreatePostResult left = new CreatePostResult(1L, false, 0L, "게시글 작성 완료");
    CreatePostResult same = new CreatePostResult(1L, false, 0L, "게시글 작성 완료");
    CreatePostResult different = new CreatePostResult(1L, true, 10L, "게시글 작성 완료! (+10 XP)");

    assertThat(left).isEqualTo(same).hasSameHashCodeAs(same);
    assertThat(left).isNotEqualTo(different);
  }
}
