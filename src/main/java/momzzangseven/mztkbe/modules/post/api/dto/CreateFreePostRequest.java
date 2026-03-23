package momzzangseven.mztkbe.modules.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record CreateFreePostRequest(
    @NotBlank(message = "Content must not be blank.") String content,
    List<
            @NotNull(message = "Image ID must not be null.")
            @Positive(message = "Image ID must be positive.") Long>
        imageIds,
    List<String> tags) {

  public CreatePostCommand toCommand(Long userId) {
    return CreatePostCommand.of(
        userId,
        null, // 자유게시판은 제목 없음
        this.content,
        PostType.FREE, // PostType 고정
        0L, // 리워드 0 고정
        this.imageIds,
        this.tags);
  }

  public boolean hasTags() {
    return tags != null && !tags.isEmpty();
  }
}
