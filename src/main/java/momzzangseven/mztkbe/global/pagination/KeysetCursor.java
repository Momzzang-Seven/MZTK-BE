package momzzangseven.mztkbe.global.pagination;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;

public record KeysetCursor(LocalDateTime createdAt, long id, String scope) {

  public KeysetCursor {
    if (createdAt == null) {
      throw new InvalidCursorException("cursor createdAt is required");
    }
    if (id <= 0) {
      throw new InvalidCursorException("cursor id must be positive");
    }
    if (scope == null || scope.isBlank()) {
      throw new InvalidCursorException("cursor scope is required");
    }
  }
}
