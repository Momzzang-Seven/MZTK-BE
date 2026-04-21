package momzzangseven.mztkbe.modules.comment.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentMutationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CommentMutationResponse unit test")
class CommentMutationResponseTest {

  @Test
  @DisplayName("from() maps mutation result without query-only fields")
  void from_mapsMutationResultWithoutQueryOnlyFields() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 4, 21, 10, 0);
    LocalDateTime updatedAt = LocalDateTime.of(2026, 4, 21, 11, 0);
    CommentMutationResult result =
        new CommentMutationResult(1L, "content", 2L, 3L, false, createdAt, updatedAt);

    CommentMutationResponse response = CommentMutationResponse.from(result);

    assertThat(response.commentId()).isEqualTo(1L);
    assertThat(response.content()).isEqualTo("content");
    assertThat(response.writerId()).isEqualTo(2L);
    assertThat(response.parentId()).isEqualTo(3L);
    assertThat(response.isDeleted()).isFalse();
    assertThat(response.createdAt()).isEqualTo(createdAt);
    assertThat(response.updatedAt()).isEqualTo(updatedAt);
  }
}
