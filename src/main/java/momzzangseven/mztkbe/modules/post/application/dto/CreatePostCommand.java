package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record CreatePostCommand(
    Long userId,
    String title,
    String content,
    PostType type, // FREE or QUESTION
    Long reward, // 질문일 때만 존재 (Nullable)
    List<String> imageUrls) {
  // 정적 팩토리 메서드
  public static CreatePostCommand of(
      Long userId,
      String title,
      String content,
      PostType type,
      Long reward,
      List<String> imageUrls) {
    return new CreatePostCommand(userId, title, content, type, reward, imageUrls);
  }
}
