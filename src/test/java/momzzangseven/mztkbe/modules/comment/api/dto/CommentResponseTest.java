package momzzangseven.mztkbe.modules.comment.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentResponse unit test")
class CommentResponseTest {

  @Nested
  @DisplayName("from(CommentResult)")
  class From {

    @Test
    @DisplayName("returns masked content and null writerId when comment is deleted")
    void from_returnsMaskedContentAndNullWriterId_whenDeleted() {
      LocalDateTime createdAt = LocalDateTime.of(2026, 3, 10, 9, 0);
      LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 11, 10, 30);
      CommentResult result =
          new CommentResult(1L, "original", 200L, null, true, createdAt, updatedAt);

      CommentResponse response = CommentResponse.from(result);

      assertThat(response.commentId()).isEqualTo(1L);
      assertThat(response.content()).isEqualTo("삭제된 댓글입니다.");
      assertThat(response.writerId()).isNull();
      assertThat(response.parentId()).isNull();
      assertThat(response.isDeleted()).isTrue();
      assertThat(response.createdAt()).isEqualTo(createdAt);
      assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("preserves content and writerId when comment is not deleted")
    void from_preservesContentAndWriterId_whenNotDeleted() {
      LocalDateTime createdAt = LocalDateTime.of(2026, 3, 12, 11, 15);
      LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 13, 12, 45);
      CommentResult result =
          new CommentResult(2L, "active comment", 300L, 100L, false, createdAt, updatedAt);

      CommentResponse response = CommentResponse.from(result);

      assertThat(response.commentId()).isEqualTo(2L);
      assertThat(response.content()).isEqualTo("active comment");
      assertThat(response.writerId()).isEqualTo(300L);
      assertThat(response.parentId()).isEqualTo(100L);
      assertThat(response.isDeleted()).isFalse();
      assertThat(response.createdAt()).isEqualTo(createdAt);
      assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }
  }
}
