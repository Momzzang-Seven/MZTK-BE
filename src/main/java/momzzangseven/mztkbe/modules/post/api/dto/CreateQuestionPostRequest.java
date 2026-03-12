package momzzangseven.mztkbe.modules.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.hibernate.validator.constraints.URL;

public record CreateQuestionPostRequest(
    @NotBlank(message = "Title must not be blank.")
        @Size(max = 255, message = "Title must not exceed 255 characters.")
        String title,
    @NotBlank(message = "Content must not be blank.") String content,
    @NotNull(message = "Reward must be provided.")
        @Positive(message = "Reward must be greater than 0.")
        Long reward,
    List<@URL(message = "Invalid URL format.") String> imageUrls,
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
