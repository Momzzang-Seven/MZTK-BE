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
    return new CreatePostCommand(userId, title, content, type, reward, imageUrls, tags);
  }

  public void validate() {
    if (title == null || title.isBlank()) {
      throw new PostInvalidInputException("Title is required");
    }
    if (content == null || content.isBlank()) {
      throw new PostInvalidInputException("Content is required");
    }

    if (type == PostType.QUESTION && (reward == null || reward < 0)) {
      throw new PostInvalidInputException("Questions must have a valid reward");
    }
  }
}
