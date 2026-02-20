package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;

public record UpdatePostCommand(
    String title, String content, List<String> imageUrls, List<String> tags) {

  public void validate() {
    if (title != null && title.isBlank()) {
      throw new PostInvalidInputException("수정할 제목은 비워둘 수 없습니다.");
    }

    if (content != null && content.isBlank()) {
      throw new PostInvalidInputException("수정할 내용은 비워둘 수 없습니다.");
    }

    if (title == null && content == null && imageUrls == null && tags == null) {
      throw new PostInvalidInputException("수정할 값이 없습니다.");
    }
  }

  public static UpdatePostCommand of(
      String title, String content, List<String> imageUrls, List<String> tags) {
    return new UpdatePostCommand(title, content, imageUrls, tags);
  }
}
