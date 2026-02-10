package momzzangseven.mztkbe.modules.post.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.PostResult;
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

  public static PostResponse from(PostResult result) {
    QuestionInfo questionInfo = null;
    if (result.type() == PostType.QUESTION) {

      questionInfo = new QuestionInfo(result.reward(), Boolean.TRUE.equals(result.isSolved()));
    }

    WriterInfo writerInfo = new WriterInfo(result.userId(), "알수없음", null);

    return new PostResponse(
        result.postId(),
        result.type(),
        result.title(),
        result.content(),
        0,
        false,
        0,
        result.createdAt(),
        result.updatedAt(),
        questionInfo,
        writerInfo,
        result.imageUrls());
  }
}
