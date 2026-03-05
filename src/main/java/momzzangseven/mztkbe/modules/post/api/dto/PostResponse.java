package momzzangseven.mztkbe.modules.post.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.PostResult;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record PostResponse(
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

  // 1. 상세 조회용 (PostResult → Response)
  public static PostResponse from(PostResult result) {
    QuestionInfo questionInfo = null;

    if (PostType.QUESTION.equals(result.type())) {
      questionInfo = new QuestionInfo(result.reward(), result.isSolved());
    }

    return new PostResponse(
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

  // 2. 목록 조회용 (Domain Post → Response)
  public static PostResponse from(Post post) {
    return new PostResponse(
        post.getId(),
        post.getType(),
        post.getTitle(),
        post.getContent(),
        0,
        false,
        0,
        post.getTags(),
        post.getCreatedAt(),
        post.getUpdatedAt(),
        new WriterInfo(post.getUserId(), "알수없음", null),
        post.getImageUrls(),
        null);
  }
}
