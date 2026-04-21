package momzzangseven.mztkbe.modules.comment.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CommentMutationResult unit test")
class CommentMutationResultTest {

  @Test
  @DisplayName("from() maps mutation fields from Comment domain")
  void from_mapsMutationFields() {
    LocalDateTime createdAt = LocalDateTime.now().minusHours(1);
    LocalDateTime updatedAt = LocalDateTime.now();
    Comment comment =
        Comment.builder()
            .id(1L)
            .postId(10L)
            .writerId(20L)
            .parentId(30L)
            .content("hello")
            .isDeleted(false)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();

    CommentMutationResult result = CommentMutationResult.from(comment);

    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.content()).isEqualTo("hello");
    assertThat(result.writerId()).isEqualTo(20L);
    assertThat(result.parentId()).isEqualTo(30L);
    assertThat(result.isDeleted()).isFalse();
    assertThat(result.createdAt()).isEqualTo(createdAt);
    assertThat(result.updatedAt()).isEqualTo(updatedAt);
  }

  @Test
  @DisplayName("from() with null comment throws NullPointerException")
  void from_withNullComment_throwsNullPointerException() {
    assertThatThrownBy(() -> CommentMutationResult.from(null))
        .isInstanceOf(NullPointerException.class);
  }
}
