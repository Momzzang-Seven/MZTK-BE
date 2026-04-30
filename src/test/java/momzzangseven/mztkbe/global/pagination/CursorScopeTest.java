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
    String userOneQuestionSearch = CursorScope.likedPosts(1L, PostType.QUESTION.name(), "form");
    String userOneQuestionNormalized =
        CursorScope.likedPosts(1L, PostType.QUESTION.name(), " FoRm ");

    assertThat(userOneFree).isNotBlank();
    assertThat(userOneFree).isNotEqualTo(userOneQuestion);
    assertThat(userOneFree).isNotEqualTo(userTwoFree);
    assertThat(userOneQuestion).isNotEqualTo(userOneQuestionSearch);
    assertThat(userOneQuestionSearch).isEqualTo(userOneQuestionNormalized);
  }

  @Test
  @DisplayName("my posts scope changes by user, type, tag, and effective search")
  void myPostsScopeIncludesUserTypeTagAndSearch() {
    String base = CursorScope.myPosts(1L, PostType.QUESTION.name(), "squat", "stance");
    String otherUser = CursorScope.myPosts(2L, PostType.QUESTION.name(), "squat", "stance");
    String otherType = CursorScope.myPosts(1L, PostType.FREE.name(), "squat", "stance");
    String otherTag = CursorScope.myPosts(1L, PostType.QUESTION.name(), "bench", "stance");
    String otherSearch = CursorScope.myPosts(1L, PostType.QUESTION.name(), "squat", "depth");
    String normalized = CursorScope.myPosts(1L, PostType.QUESTION.name(), " SQUAT ", " Stance ");

    assertThat(base).isNotBlank();
    assertThat(base).isNotEqualTo(otherUser);
    assertThat(base).isNotEqualTo(otherType);
    assertThat(base).isNotEqualTo(otherTag);
    assertThat(base).isNotEqualTo(otherSearch);
    assertThat(base).isEqualTo(normalized);
  }

  @Test
  @DisplayName("commented posts scope changes by search and keeps blank search compatible")
  void commentedPostsScopeIncludesSearchWhenPresent() {
    String noSearch = CursorScope.commentedPosts(1L, PostType.QUESTION.name());
    String blankSearch = CursorScope.commentedPosts(1L, PostType.QUESTION.name(), " ");
    String formSearch = CursorScope.commentedPosts(1L, PostType.QUESTION.name(), " FoRm ");
    String otherSearch = CursorScope.commentedPosts(1L, PostType.QUESTION.name(), "other");

    assertThat(blankSearch).isEqualTo(noSearch);
    assertThat(formSearch).isNotEqualTo(noSearch);
    assertThat(formSearch).isNotEqualTo(otherSearch);
    assertThat(formSearch).isEqualTo(CursorScope.commentedPosts(1L, "question", "form"));
  }
}
