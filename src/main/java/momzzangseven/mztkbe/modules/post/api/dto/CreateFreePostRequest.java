package momzzangseven.mztkbe.modules.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.hibernate.validator.constraints.URL;

public record CreateFreePostRequest(
    @NotBlank(message = "Content must not be blank.") String content,
    List<@URL(message = "Invalid image URL format.") String> imageUrls,
    List<String> tags) {

  public CreatePostCommand toCommand(Long userId) {
    return CreatePostCommand.of(
        userId,
        null, // 자유게시판은 제목 없음
        this.content,
        PostType.FREE, // PostType 고정
        0L, // 리워드 0 고정
        this.imageUrls,
        this.tags);
  }

  public boolean hasTags() {
    return tags != null && !tags.isEmpty();
  }
}
