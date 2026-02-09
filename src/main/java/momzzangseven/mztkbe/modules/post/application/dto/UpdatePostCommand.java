package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.List;

public record UpdatePostCommand(String title, String content, List<String> imageUrls) {
  public static UpdatePostCommand of(String title, String content, List<String> imageUrls) {
    return new UpdatePostCommand(title, content, imageUrls);
  }
}
