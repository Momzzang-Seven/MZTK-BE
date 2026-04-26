package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record GetMyCommentedPostsCursorCommand(
    Long requesterId, PostType type, String cursor, Integer size) {

  private static final int DEFAULT_SIZE = 10;
  private static final int MAX_SIZE = 50;

  public void validate() {
    if (requesterId == null || requesterId <= 0) {
      throw new PostInvalidInputException("Requester id is required.");
    }
    if (type == null) {
      throw new PostInvalidInputException("Post type is required.");
    }
  }

  public CursorPageRequest pageRequest() {
    validate();
    return CursorPageRequest.of(
        cursor, size, DEFAULT_SIZE, MAX_SIZE, CursorScope.commentedPosts(requesterId, type.name()));
  }
}
