package momzzangseven.mztkbe.modules.post.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record PostResponse(
    Long postId,
    PostType type,
    String title,
    String content,
    int likeCount,
    boolean isLiked, // 좋아요 기능 구현 후 연동
    int commentCount, // 댓글 기능 구현 후 연동
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) QuestionInfo question, // 질문일 때만 포함
    WriterInfo writer, // 작성자 정보
    List<String> imageUrls // (TODO) 이미지 모듈 연동
    ) {
  // 내부 레코드: 질문 정보
  public record QuestionInfo(Long reward, boolean isSolved) {}

  // 내부 레코드: 작성자 정보
  public record WriterInfo(Long userId, String nickname, String profileImage) {}

  // --- Entity -> DTO 변환 메서드 (Factory) ---
  public static PostResponse from(Post post) {
    // 질문 정보 추출 (질문 게시판이 아니면 null)
    QuestionInfo questionInfo = null;
    if (post.getType() == PostType.QUESTION) {
      questionInfo = new QuestionInfo(post.getReward(), post.getIsSolved());
    }

    // 작성자 정보 (일단 ID만 넣고 나머지는 더미 데이터. 나중에 User 모듈에서 가져와야 함)
    WriterInfo writerInfo = new WriterInfo(post.getUserId(), "알수없음", null);

    return new PostResponse(
        post.getId(),
        post.getType(),
        post.getTitle(),
        post.getContent(),
        post.getViewCount(), // likeCount 대신 일단 viewCount 넣거나 0 처리
        false, // isLiked (미구현)
        0, // commentCount (미구현)
        post.getCreatedAt(),
        post.getUpdatedAt(),
        questionInfo,
        writerInfo,
        List.of() // imageUrls (미구현)
        );
  }
}
