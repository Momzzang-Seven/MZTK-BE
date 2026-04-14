package momzzangseven.mztkbe.modules.post.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CreateFreePostRequest 단위 테스트")
class CreateFreePostRequestTest {

  @Nested
  @DisplayName("hasTags()")
  class HasTags {

    @Test
    @DisplayName("tags=null이면 false")
    void nullTags_returnsFalse() {
      CreateFreePostRequest request = new CreateFreePostRequest("내용", null, null);
      assertThat(request.hasTags()).isFalse();
    }

    @Test
    @DisplayName("tags=빈 리스트이면 false")
    void emptyTags_returnsFalse() {
      CreateFreePostRequest request = new CreateFreePostRequest("내용", null, List.of());
      assertThat(request.hasTags()).isFalse();
    }

    @Test
    @DisplayName("tags가 있으면 true")
    void nonEmptyTags_returnsTrue() {
      CreateFreePostRequest request =
          new CreateFreePostRequest("내용", null, List.of("spring", "java"));
      assertThat(request.hasTags()).isTrue();
    }
  }

  @Nested
  @DisplayName("toCommand()")
  class ToCommand {

    @Test
    @DisplayName("toCommand_mapsFreePostFields")
    void toCommand_mapsFreePostFields() {
      CreateFreePostRequest request =
          new CreateFreePostRequest("자유게시판 내용", List.of(1L, 2L), List.of("tag1"));

      CreatePostCommand command = request.toCommand(42L);

      assertThat(command.userId()).isEqualTo(42L);
      assertThat(command.title()).isNull();
      assertThat(command.content()).isEqualTo("자유게시판 내용");
      assertThat(command.type()).isEqualTo(PostType.FREE);
      assertThat(command.reward()).isZero();
      assertThat(command.imageIds()).containsExactly(1L, 2L);
      assertThat(command.tags()).containsExactly("tag1");
    }

    @Test
    @DisplayName("imageIds=null이면 커맨드의 imageIds도 null")
    void toCommand_nullImageIds_passedThrough() {
      CreateFreePostRequest request = new CreateFreePostRequest("내용", null, null);

      CreatePostCommand command = request.toCommand(1L);

      assertThat(command.imageIds()).isNull();
    }
  }
}
