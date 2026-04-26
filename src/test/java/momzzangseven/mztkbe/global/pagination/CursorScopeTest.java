package momzzangseven.mztkbe.global.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CursorScope unit test")
class CursorScopeTest {

  @Test
  @DisplayName("liked posts scope changes by user and type")
  void likedPostsScopeIncludesUserAndType() {
    String userOneFree = CursorScope.likedPosts(1L, PostType.FREE.name());
    String userOneQuestion = CursorScope.likedPosts(1L, PostType.QUESTION.name());
    String userTwoFree = CursorScope.likedPosts(2L, PostType.FREE.name());

    assertThat(userOneFree).isNotBlank();
    assertThat(userOneFree).isNotEqualTo(userOneQuestion);
    assertThat(userOneFree).isNotEqualTo(userTwoFree);
  }
}
