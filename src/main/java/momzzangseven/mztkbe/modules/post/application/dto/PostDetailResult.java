package momzzangseven.mztkbe.modules.post.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionResumeView;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record PostDetailResult(
    Long postId,
    PostType type,
    String title,
    String content,
    long likeCount,
    long commentCount,
    boolean liked,
    Long userId,
    String nickname,
    String profileImageUrl,
    List<PostImageResult.PostImageSlot> images,
    Long reward,
    boolean isSolved,
    QuestionExecutionResumeView web3Execution,
    List<String> tags,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public PostDetailResult {
    images = images == null ? List.of() : images;
  }

  public static PostDetailResult fromDomain(
      Post post,
      long likeCount,
      long commentCount,
      boolean liked,
      String nickname,
      String profileImageUrl,
      List<PostImageResult.PostImageSlot> images,
      QuestionExecutionResumeView web3Execution) {
    return new PostDetailResult(
        post.getId(),
        post.getType(),
        post.getTitle(),
        post.getContent(),
        likeCount,
        commentCount,
        liked,
        post.getUserId(),
        nickname,
        profileImageUrl,
        images,
        post.getReward(),
        post.getIsSolved(),
        web3Execution,
        post.getTags(),
        post.getCreatedAt(),
        post.getUpdatedAt());
  }
}
