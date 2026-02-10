package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;

public record UpdatePostCommand(String title, String content, List<String> imageUrls) {

  public void validate() {
    if (title == null || title.isBlank()) {
      throw new PostInvalidInputException("수정할 제목은 필수입니다.");
    }
    if (content == null || content.isBlank()) {
      throw new PostInvalidInputException("수정할 내용은 필수입니다.");
    }
  }

  public static UpdatePostCommand of(String title, String content, List<String> imageUrls) {
    return new UpdatePostCommand(title, content, imageUrls);
  }
}
