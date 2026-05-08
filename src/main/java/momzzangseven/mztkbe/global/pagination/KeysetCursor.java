package momzzangseven.mztkbe.global.pagination;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;

/**
 * Opaque keyset cursor for stable cursor-based (keyset) pagination.
 *
 * <p>{@code createdAt} is a <b>generic temporal sort key</b> — its semantics depend on the
 * calling context:
 *
 * <ul>
 *   <li>{@code post} module → entity {@code created_at}
 *   <li>{@code comment} module → entity {@code created_at}
 *   <li>{@code reservation} module → {@code reservation_date.atStartOfDay()} (NOT entity
 *       created_at)
 * </ul>
 *
 * <p>Callers that encode a cursor must use the same temporal field that drives the {@code ORDER BY}
 * clause in the persistence query. Mismatches silently produce incorrect pages.
 *
 * @param createdAt generic temporal sort key (semantics are context-dependent — see above)
 * @param id tie-breaker; must be positive
 * @param scope prevents cursor from being replayed in a different query context
 */
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
