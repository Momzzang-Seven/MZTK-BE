package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record PostCursorSearchCondition(
    PostType type, String tagName, String search, CursorPageRequest pageRequest) {

  private static final int DEFAULT_SIZE = 10;
  private static final int MAX_SIZE = 50;

  public static PostCursorSearchCondition of(
      PostType type, String tag, String search, String cursor, Integer size) {
    String normalizedTag = normalizeTag(tag);
    String normalizedSearch = normalizeSearch(type, search);
    String scope =
        CursorScope.posts(type == null ? null : type.name(), normalizedTag, normalizedSearch);
    CursorPageRequest pageRequest =
        CursorPageRequest.of(cursor, size, DEFAULT_SIZE, MAX_SIZE, scope);
    return new PostCursorSearchCondition(type, normalizedTag, normalizedSearch, pageRequest);
  }

  public int size() {
    return pageRequest.size();
  }

  private static String normalizeTag(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toLowerCase();
  }

  private static String normalizeSearch(PostType type, String value) {
    if (value == null || value.isBlank() || type == PostType.FREE) {
      return null;
    }
    return value.trim().toLowerCase();
  }
}
