package momzzangseven.mztkbe.global.pagination;

import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;

public record CursorPageRequest(KeysetCursor cursor, int size, String scope) {

  public CursorPageRequest {
    if (size <= 0) {
      throw new InvalidCursorException("size must be positive");
    }
    if (scope == null || scope.isBlank()) {
      throw new InvalidCursorException("cursor scope is required");
    }
  }

  public static CursorPageRequest of(
      String encodedCursor, Integer requestedSize, int defaultSize, int maxSize, String scope) {
    int resolvedSize = requestedSize == null ? defaultSize : requestedSize;
    if (resolvedSize < 1 || resolvedSize > maxSize) {
      throw new InvalidCursorException("size must be between 1 and " + maxSize);
    }
    return new CursorPageRequest(CursorCodec.decode(encodedCursor, scope), resolvedSize, scope);
  }

  public int limitWithProbe() {
    return size + 1;
  }

  public boolean hasCursor() {
    return cursor != null;
  }
}
