package momzzangseven.mztkbe.modules.post.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.PostDetailResult;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record PostDetailResponse(
    Long postId,
    PostType type,
    String title,
    String content,
    int likeCount,
    boolean isLiked,
    int commentCount,
    List<String> tags,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    WriterInfo writer,
    List<String> imageUrls,
    QuestionInfo question) {

  public record WriterInfo(Long userId, String nickname, String profileImage) {}

  public record QuestionInfo(Long reward, boolean isSolved) {}

  public static PostDetailResponse from(PostDetailResult result) {
    QuestionInfo questionInfo = null;

    if (PostType.QUESTION.equals(result.type())) {
      questionInfo = new QuestionInfo(result.reward(), result.isSolved());
    }

    return new PostDetailResponse(
        result.postId(),
        result.type(),
        result.title(),
        result.content(),
        0,
        false,
        0,
        result.tags(),
        result.createdAt(),
        result.updatedAt(),
        new WriterInfo(result.userId(), result.nickname(), result.profileImageUrl()),
        result.imageUrls(),
        questionInfo);
  }
}
