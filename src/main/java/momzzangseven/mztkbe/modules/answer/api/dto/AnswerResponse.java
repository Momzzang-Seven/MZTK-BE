package momzzangseven.mztkbe.modules.answer.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerImageResult;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerResult;

public record AnswerResponse(
    Long answerId,
    Long userId,
    String nickname,
    String profileImageUrl,
    String content,
    boolean isAccepted,
    long likeCount,
    boolean isLiked,
    List<ImageItem> images,
    AnswerWeb3ExecutionResponse web3Execution,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public record ImageItem(Long imageId, String imageUrl) {
    public static ImageItem from(AnswerImageResult.AnswerImageSlot slot) {
      return new ImageItem(slot.imageId(), slot.imageUrl());
    }
  }

  public static AnswerResponse from(AnswerResult answer) {
    List<ImageItem> images =
        answer.images() == null
            ? List.of()
            : answer.images().stream().map(ImageItem::from).toList();

    return new AnswerResponse(
        answer.answerId(),
        answer.userId(),
        answer.nickname(),
        answer.profileImageUrl(),
        answer.content(),
        answer.accepted(),
        answer.likeCount(),
        answer.liked(),
        images,
        AnswerWeb3ExecutionResponse.from(answer.web3Execution()),
        answer.createdAt(),
        answer.updatedAt());
  }
}
