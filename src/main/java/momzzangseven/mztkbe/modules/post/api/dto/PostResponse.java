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
    // 1. 질문 게시글인 경우 정보 추출
    QuestionInfo questionInfo = null;
    if (result.type() == PostType.QUESTION) {
      questionInfo = new QuestionInfo(result.reward(), result.isSolved());
    }

    // 2. 작성자 정보 (현재는 임시 데이터, 나중에 User 모듈 연동 필요)

    WriterInfo writerInfo = new WriterInfo(result.userId(), "알수없음", null);

    return new PostResponse(
        result.postId(),
        result.type(),
        result.title(),
        result.content(),
        0, // likeCount (추후 연동)
        false, // isLiked (추후 연동)
        0, // commentCount (추후 연동)
        result.createdAt(),
        result.updatedAt(),
        questionInfo,
        writerInfo,
        result.imageUrls());
  }
}
