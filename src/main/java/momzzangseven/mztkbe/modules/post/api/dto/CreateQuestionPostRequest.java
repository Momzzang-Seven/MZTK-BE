package momzzangseven.mztkbe.modules.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.hibernate.validator.constraints.URL;

public record CreateQuestionPostRequest(
    @NotBlank(message = "제목을 입력해주세요.") String title,
    @NotBlank(message = "내용을 입력해주세요.") String content,
    @NotNull(message = "보상 MZT를 입력해주세요.") @Positive(message = "보상 MZT는 1 이상이어야 합니다.") Long reward,
    List<@URL(message = "올바른 이미지 URL 형식이 아닙니다.") String> imageUrls,
    List<String> tags) {

  public CreatePostCommand toCommand(Long userId) {
    return CreatePostCommand.of(
        userId,
        this.title,
        this.content,
        PostType.QUESTION, // PostType 고정
        this.reward,
        this.imageUrls,
        this.tags);
  }

  public boolean hasTags() {
    return tags != null && !tags.isEmpty();
  }
}
