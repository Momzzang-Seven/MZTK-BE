package momzzangseven.mztkbe.modules.post.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) QuestionInfo question,
    WriterInfo writer,
    List<String> imageUrls) {
  public record QuestionInfo(Long reward, boolean isSolved) {}

  public record WriterInfo(Long userId, String nickname, String profileImage) {}

  public static PostResponse from(Post post) {
    // 1. 질문 정보 추출
    QuestionInfo questionInfo = null;
    if (post.getType() == PostType.QUESTION) {
      questionInfo = new QuestionInfo(post.getReward(), post.getIsSolved());
    }

    // 2. 작성자 정보 (나중에 User 모듈 연동 필요)
    WriterInfo writerInfo = new WriterInfo(post.getUserId(), "알수없음", null);

    List<String> images =
        (post.getImageUrls() != null) ? new ArrayList<>(post.getImageUrls()) : List.of();

    return new PostResponse(
        post.getId(),
        post.getType(),
        post.getTitle(),
        post.getContent(),
        0, // likeCount
        false,
        0,
        post.getCreatedAt(),
        post.getUpdatedAt(),
        questionInfo,
        writerInfo,
        images);
  }
}
