package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.HashSet;
import java.util.List;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record CreatePostCommand(
    Long userId,
    String title,
    String content,
    PostType type,
    Long reward,
    List<Long> imageIds,
    List<String> tags) {

  public static CreatePostCommand of(
      Long userId,
      String title,
      String content,
      PostType type,
      Long reward,
      List<Long> imageIds,
      List<String> tags) {

    String finalTitle = (type == PostType.QUESTION) ? title : null;
    return new CreatePostCommand(userId, finalTitle, content, type, reward, imageIds, tags);
  }

  public void validate() {
    if (content == null || content.isBlank()) {
      throw new PostInvalidInputException("Content is required");
    }
    if (imageIds != null && new HashSet<>(imageIds).size() != imageIds.size()) {
      throw new PostInvalidInputException("Duplicate image IDs are not allowed");
    }
    if (type == PostType.QUESTION) {

      if (title == null || title.isBlank()) {
        throw new PostInvalidInputException("Title is required for question board");
      }
      if (reward == null || reward <= 0) {
        throw new PostInvalidInputException("Questions must have a valid reward");
      }
    } else if (type == PostType.FREE) {
      if (title != null) {
        throw new PostInvalidInputException("Title must be null for free board");
      }
      if (reward == null || reward != 0L) {
        throw new PostInvalidInputException("Free posts must have zero reward");
      }
    }
  }
}
