package momzzangseven.mztkbe.global.pagination;

import java.util.List;

public record CursorSlice<T>(List<T> items, boolean hasNext, String nextCursor) {

  public CursorSlice {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
