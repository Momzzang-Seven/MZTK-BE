package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.Locale;
import momzzangseven.mztkbe.global.error.post.InvalidLikedPostsQueryException;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record GetMyLikedPostsCursorCommand(
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
    if (type == PostType.FREE) {
      return null;
    }
    return normalize(search);
  }

  private String scope() {
    return CursorScope.likedPosts(requesterId, type.name(), effectiveSearch());
  }

  private void validateNonCursorInput() {
    if (requesterId == null || requesterId <= 0) {
      throw new InvalidLikedPostsQueryException("requesterId must be positive.");
    }
    if (type == null) {
      throw new InvalidLikedPostsQueryException("type is required.");
    }
    if (type != PostType.FREE && type != PostType.QUESTION) {
      throw new InvalidLikedPostsQueryException("type must be FREE or QUESTION.");
    }
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
