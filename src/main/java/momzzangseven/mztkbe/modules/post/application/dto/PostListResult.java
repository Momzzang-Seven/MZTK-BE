package momzzangseven.mztkbe.modules.post.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record PostListResult(
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
    Long reward,
    boolean isSolved,
    List<String> tags,
    List<PostImageResult.PostImageSlot> images,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public PostListResult {
    images = images == null ? List.of() : images;
  }

  public PostListResult(
      Long postId,
      PostType type,
      String title,
      String content,
      long likeCount,
      boolean liked,
      Long userId,
      String nickname,
      String profileImageUrl,
      Long reward,
      boolean isSolved,
      List<String> tags,
      List<PostImageResult.PostImageSlot> images,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this(
        postId,
        type,
        title,
        content,
        likeCount,
        0L,
        liked,
        userId,
        nickname,
        profileImageUrl,
        reward,
        isSolved,
        tags,
        images,
        createdAt,
        updatedAt);
  }

  public static PostListResult fromDomain(
      Post post,
      long likeCount,
      boolean liked,
      String nickname,
      String profileImageUrl,
      List<PostImageResult.PostImageSlot> images) {
    return fromDomain(post, likeCount, 0L, liked, nickname, profileImageUrl, images);
  }

  public static PostListResult fromDomain(
      Post post,
      long likeCount,
      long commentCount,
      boolean liked,
      String nickname,
      String profileImageUrl,
      List<PostImageResult.PostImageSlot> images) {
    return new PostListResult(
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
        post.getReward(),
        post.getIsSolved(),
        post.getTags(),
        images,
        post.getCreatedAt(),
        post.getUpdatedAt());
  }
}
