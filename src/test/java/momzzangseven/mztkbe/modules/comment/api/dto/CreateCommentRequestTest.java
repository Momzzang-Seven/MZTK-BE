package momzzangseven.mztkbe.modules.comment.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateCommentRequest unit test")
class CreateCommentRequestTest {

  @Nested
  @DisplayName("toCommand(Long postId, Long userId)")
  class ToCommand {

    @Test
    @DisplayName("maps top-level comment request when parentId is null")
    void toCommand_mapsTopLevelCommentWhenParentIdIsNull() {
      CreateCommentRequest request = new CreateCommentRequest("top-level comment", null);

      CreateCommentCommand command = request.toCommand(10L, 20L);

      assertThat(command.postId()).isEqualTo(10L);
      assertThat(command.userId()).isEqualTo(20L);
      assertThat(command.parentId()).isNull();
      assertThat(command.content()).isEqualTo("top-level comment");
    }

    @Test
    @DisplayName("maps reply request when parentId is present")
    void toCommand_mapsReplyWhenParentIdIsPresent() {
      CreateCommentRequest request = new CreateCommentRequest("reply comment", 30L);

      CreateCommentCommand command = request.toCommand(11L, 21L);

      assertThat(command.postId()).isEqualTo(11L);
      assertThat(command.userId()).isEqualTo(21L);
      assertThat(command.parentId()).isEqualTo(30L);
      assertThat(command.content()).isEqualTo("reply comment");
    }
  }
}
