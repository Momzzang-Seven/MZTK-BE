package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record CreatePostCommand(
    Long userId,
    String title,
    String content,
    PostType type,
    Long reward,
    List<String> imageUrls,
    List<String> tags) {

  public static CreatePostCommand of(
      Long userId,
      String title,
      String content,
      PostType type,
      Long reward,
      List<String> imageUrls,
      List<String> tags) {

    return new CreatePostCommand(userId, null, content, type, reward, imageUrls, tags);
  }

  public void validate() {
    if (content == null || content.isBlank()) {
      throw new PostInvalidInputException("Content is required");
    }
  }
}
