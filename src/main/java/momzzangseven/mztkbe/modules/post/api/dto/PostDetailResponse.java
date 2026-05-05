package momzzangseven.mztkbe.modules.post.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.response.ImageItemResponse;
import momzzangseven.mztkbe.modules.post.application.dto.PostDetailResult;
import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record PostDetailResponse(
    Long postId,
    PostType type,
    String title,
    String content,
    long likeCount,
    boolean isLiked,
    long commentCount,
    PostPublicationStatus publicationStatus,
    PostModerationStatus moderationStatus,
    List<String> tags,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    WriterInfo writer,
    List<ImageItemResponse> images,
    QuestionInfo question) {

  public record WriterInfo(Long userId, String nickname, String profileImage) {}

  public record QuestionInfo(
      Long reward, boolean isSolved, QuestionWeb3ExecutionResponse web3Execution) {}

  public static PostDetailResponse from(PostDetailResult result) {
    QuestionInfo questionInfo = null;

    if (PostType.QUESTION.equals(result.type())) {
      questionInfo =
          new QuestionInfo(
              result.reward(),
              result.isSolved(),
              QuestionWeb3ExecutionResponse.from(result.web3Execution()));
    }

    List<ImageItemResponse> images =
        result.images().stream()
            .map(slot -> new ImageItemResponse(slot.imageId(), slot.imageUrl()))
            .toList();

    return new PostDetailResponse(
        result.postId(),
        result.type(),
        result.title(),
        result.content(),
        result.likeCount(),
        result.liked(),
        result.commentCount(),
        result.publicationStatus(),
        result.moderationStatus(),
        result.tags(),
        result.createdAt(),
        result.updatedAt(),
        new WriterInfo(result.userId(), result.nickname(), result.profileImageUrl()),
        images,
        questionInfo);
  }
}
