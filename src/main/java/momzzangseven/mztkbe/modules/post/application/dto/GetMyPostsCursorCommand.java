package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.Locale;
import momzzangseven.mztkbe.global.error.post.InvalidMyPostsQueryException;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record GetMyPostsCursorCommand(
    Long requesterId, PostType type, String tag, String search, String cursor, Integer size) {

  private static final int DEFAULT_SIZE = 10;
  private static final int MAX_SIZE = 50;

  public void validate() {
    pageRequest();
  }

  public CursorPageRequest pageRequest() {
    validateNonCursorInput();
    return CursorPageRequest.of(cursor, size, DEFAULT_SIZE, MAX_SIZE, scope());
  }

  public String tagName() {
    return normalize(tag);
  }

  public String effectiveSearch() {
    if (type == PostType.FREE) {
      return null;
    }
    return normalize(search);
  }

  private String scope() {
    return CursorScope.myPosts(requesterId, type.name(), tagName(), effectiveSearch());
  }

  private void validateNonCursorInput() {
    if (requesterId == null || requesterId <= 0) {
      throw new InvalidMyPostsQueryException("requesterId must be positive.");
    }
    if (type == null) {
      throw new InvalidMyPostsQueryException("type is required.");
    }
    if (type != PostType.FREE && type != PostType.QUESTION) {
      throw new InvalidMyPostsQueryException("type must be FREE or QUESTION.");
    }
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
