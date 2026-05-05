package momzzangseven.mztkbe.modules.post.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.RecoverQuestionPostEscrowCommand;

/** Optional request body for failed question create recovery. */
public record RecoverQuestionCreateRequest(
    String title,
    String content,
    List<
            @NotNull(message = "Image ID must not be null.")
            @Positive(message = "Image ID must be positive.") Long>
        imageIds,
    List<@NotBlank(message = "Tag must not be blank.") String> tags) {

  public static RecoverQuestionCreateRequest empty() {
    return new RecoverQuestionCreateRequest(null, null, null, null);
  }

  public RecoverQuestionPostEscrowCommand toCommand(Long requesterId, Long postId) {
    return new RecoverQuestionPostEscrowCommand(
        requesterId, postId, title, content, imageIds, tags);
  }
}
