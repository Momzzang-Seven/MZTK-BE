package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;

public record UpdatePostCommand(
    String title, String content, List<Long> imageIds, List<String> tags) {

  public void validate() {
    if (title != null && title.isBlank()) {
      throw new PostInvalidInputException("Title cannot be blank.");
    }

    if (content != null && content.isBlank()) {
      throw new PostInvalidInputException("Content cannot be blank.");
    }

    if (title == null && content == null && imageIds == null && tags == null) {
      throw new PostInvalidInputException("At least one field must be provided for update.");
    }
  }

  public static UpdatePostCommand of(
      String title, String content, List<Long> imageIds, List<String> tags) {
    return new UpdatePostCommand(title, content, imageIds, tags);
  }
}
