package momzzangseven.mztkbe.modules.post.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PostLike unit test")
class PostLikeTest {

  @Test
  @DisplayName("creates post like with valid inputs")
  void create_success() {
    PostLike postLike = PostLike.create(PostLikeTargetType.POST, 10L, 7L);

    assertThat(postLike.getId()).isNull();
    assertThat(postLike.getTargetType()).isEqualTo(PostLikeTargetType.POST);
    assertThat(postLike.getTargetId()).isEqualTo(10L);
    assertThat(postLike.getUserId()).isEqualTo(7L);
    assertThat(postLike.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("throws when target type is null")
  void create_failsWhenTargetTypeIsNull() {
    assertThatThrownBy(() -> PostLike.create(null, 10L, 7L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("targetType is required.");
  }

  @Test
  @DisplayName("throws when target id is not positive")
  void create_failsWhenTargetIdIsInvalid() {
    assertThatThrownBy(() -> PostLike.create(PostLikeTargetType.POST, 0L, 7L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("targetId must be positive.");
  }

  @Test
  @DisplayName("throws when user id is not positive")
  void create_failsWhenUserIdIsInvalid() {
    assertThatThrownBy(() -> PostLike.create(PostLikeTargetType.POST, 10L, 0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("userId must be positive.");
  }
}
