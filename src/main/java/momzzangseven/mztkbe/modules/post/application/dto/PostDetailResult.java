package momzzangseven.mztkbe.modules.post.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionResumeView;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record PostDetailResult(
    Long postId,
    PostType type,
    String title,
    String content,
    long likeCount,
    long commentCount,
    long answerCount,
    boolean liked,
    Long userId,
    String nickname,
    String profileImageUrl,
    List<PostImageResult.PostImageSlot> images,
    Long reward,
    boolean isSolved,
    PostPublicationStatus publicationStatus,
    PostModerationStatus moderationStatus,
    QuestionExecutionResumeView web3Execution,
    List<String> tags,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public PostDetailResult {
    images = images == null ? List.of() : images;
  }

  public PostDetailResult(
      Long postId,
      PostType type,
      String title,
      String content,
      long likeCount,
      long commentCount,
      long answerCount,
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
    this(
        postId,
        type,
        title,
        content,
        likeCount,
        commentCount,
        answerCount,
        liked,
        userId,
        nickname,
        profileImageUrl,
        images,
        reward,
        isSolved,
        PostPublicationStatus.VISIBLE,
        PostModerationStatus.NORMAL,
        web3Execution,
        tags,
        createdAt,
        updatedAt);
  }

  public PostDetailResult(
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
    this(
        postId,
        type,
        title,
        content,
        likeCount,
        commentCount,
        0L,
        liked,
        userId,
        nickname,
        profileImageUrl,
        images,
        reward,
        isSolved,
        web3Execution,
        tags,
        createdAt,
        updatedAt);
  }

  public PostDetailResult(
      Long postId,
      PostType type,
      String title,
      String content,
      long likeCount,
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
    this(
        postId,
        type,
        title,
        content,
        likeCount,
        0L,
        0L,
        liked,
        userId,
        nickname,
        profileImageUrl,
        images,
        reward,
        isSolved,
        PostPublicationStatus.VISIBLE,
        PostModerationStatus.NORMAL,
        web3Execution,
        tags,
        createdAt,
        updatedAt);
  }

  public static PostDetailResult fromDomain(
      Post post,
      long likeCount,
      boolean liked,
      String nickname,
      String profileImageUrl,
      List<PostImageResult.PostImageSlot> images,
      QuestionExecutionResumeView web3Execution) {
    return fromDomain(post, likeCount, 0L, liked, nickname, profileImageUrl, images, web3Execution);
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
    return fromDomain(
        post, likeCount, commentCount, 0L, liked, nickname, profileImageUrl, images, web3Execution);
  }

  public static PostDetailResult fromDomain(
      Post post,
      long likeCount,
      long commentCount,
      long answerCount,
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
        answerCount,
        liked,
        post.getUserId(),
        nickname,
        profileImageUrl,
        images,
        post.getReward(),
        post.getIsSolved(),
        post.getPublicationStatus(),
        post.getModerationStatus(),
        web3Execution,
        post.getTags(),
        post.getCreatedAt(),
        post.getUpdatedAt());
  }
}
