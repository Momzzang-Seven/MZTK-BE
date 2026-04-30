package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.Locale;
import momzzangseven.mztkbe.global.error.post.InvalidCommentedPostsQueryException;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record GetMyCommentedPostsCursorCommand(
    Long requesterId, PostType type, String search, String cursor, Integer size) {

  private static final int DEFAULT_SIZE = 10;
  private static final int MAX_SIZE = 50;

  public void validate() {
    pageRequest();
  }

  public CursorPageRequest pageRequest() {
    validateNonCursorInput();
    return CursorPageRequest.of(cursor, size, DEFAULT_SIZE, MAX_SIZE, scope());
  }

  public String effectiveSearch() {
    if (type == PostType.FREE || search == null || search.isBlank()) {
      return null;
    }
    return search.trim().toLowerCase(Locale.ROOT);
  }

  private String scope() {
    return CursorScope.commentedPosts(requesterId, type.name(), effectiveSearch());
  }

  private void validateNonCursorInput() {
    if (requesterId == null || requesterId <= 0) {
      throw new InvalidCommentedPostsQueryException("requesterId must be positive.");
    }
    if (type == null) {
      throw new InvalidCommentedPostsQueryException("type is required.");
    }
    if (type != PostType.FREE && type != PostType.QUESTION) {
      throw new InvalidCommentedPostsQueryException("type must be FREE or QUESTION.");
    }
  }
}
