package momzzangseven.mztkbe.modules.post.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CreateQuestionPostRequest 단위 테스트")
class CreateQuestionPostRequestTest {

  @Nested
  @DisplayName("hasTags()")
  class HasTags {

    @Test
    @DisplayName("tags=null이면 false")
    void nullTags_returnsFalse() {
      CreateQuestionPostRequest request =
          new CreateQuestionPostRequest("제목", "내용", 10L, null, null);
      assertThat(request.hasTags()).isFalse();
    }

    @Test
    @DisplayName("tags=빈 리스트이면 false")
    void emptyTags_returnsFalse() {
      CreateQuestionPostRequest request =
          new CreateQuestionPostRequest("제목", "내용", 10L, null, List.of());
      assertThat(request.hasTags()).isFalse();
    }

    @Test
    @DisplayName("tags가 있으면 true")
    void nonEmptyTags_returnsTrue() {
      CreateQuestionPostRequest request =
          new CreateQuestionPostRequest("제목", "내용", 10L, null, List.of("java", "spring"));
      assertThat(request.hasTags()).isTrue();
    }
  }

  @Nested
  @DisplayName("toCommand()")
  class ToCommand {

    @Test
    @DisplayName("userId를 주입하고 QUESTION 타입, title, reward를 포함한 커맨드 생성")
    void toCommand_mapsQuestionPostFields() {
      CreateQuestionPostRequest request =
          new CreateQuestionPostRequest("질문 제목", "질문 내용", 50L, List.of(), List.of("java"));

      CreatePostCommand command = request.toCommand(42L);

      assertThat(command.userId()).isEqualTo(42L);
      assertThat(command.title()).isEqualTo("질문 제목");
      assertThat(command.content()).isEqualTo("질문 내용");
      assertThat(command.type()).isEqualTo(PostType.QUESTION);
      assertThat(command.reward()).isEqualTo(50L);
      assertThat(command.tags()).containsExactly("java");
    }

    @Test
    @DisplayName("imageUrls=null이면 커맨드의 imageUrls도 null")
    void toCommand_nullImageUrls_passedThrough() {
      CreateQuestionPostRequest request =
          new CreateQuestionPostRequest("제목", "내용", 10L, null, null);

      CreatePostCommand command = request.toCommand(1L);

      assertThat(command.imageUrls()).isNull();
    }

    @Test
    @DisplayName("tags=null이면 커맨드의 tags도 null")
    void toCommand_nullTags_passedThrough() {
      CreateQuestionPostRequest request =
          new CreateQuestionPostRequest("제목", "내용", 10L, null, null);

      CreatePostCommand command = request.toCommand(1L);

      assertThat(command.tags()).isNull();
    }
  }
}
