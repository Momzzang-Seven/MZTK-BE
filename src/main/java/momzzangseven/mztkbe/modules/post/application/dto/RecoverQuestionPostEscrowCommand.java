package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.HashSet;
import java.util.List;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;

/** Command for recreating a missing question-create escrow intent from local question state. */
public record RecoverQuestionPostEscrowCommand(
    Long requesterId,
    Long postId,
    String title,
    String content,
    List<Long> imageIds,
    List<String> tags) {

  public RecoverQuestionPostEscrowCommand(Long requesterId, Long postId) {
    this(requesterId, postId, null, null, null, null);
  }

  public void validate() {
    if (requesterId == null || requesterId <= 0) {
      throw new PostInvalidInputException("requesterId must be positive.");
    }
    if (postId == null || postId <= 0) {
      throw new PostInvalidInputException("postId must be positive.");
    }
    if (title != null && title.isBlank()) {
      throw new PostInvalidInputException("Title cannot be blank.");
    }
    if (content != null && content.isBlank()) {
      throw new PostInvalidInputException("Content cannot be blank.");
    }
    if (imageIds != null && imageIds.stream().anyMatch(id -> id == null || id <= 0)) {
      throw new PostInvalidInputException("Image IDs must be positive.");
    }
    if (imageIds != null && new HashSet<>(imageIds).size() != imageIds.size()) {
      throw new PostInvalidInputException("Duplicate image IDs are not allowed");
    }
    if (tags != null && tags.stream().anyMatch(tag -> tag == null || tag.isBlank())) {
      throw new PostInvalidInputException("Tag cannot be blank.");
    }
  }

  public boolean hasMutationFields() {
    return title != null || content != null || imageIds != null || tags != null;
  }
}
