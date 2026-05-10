package momzzangseven.mztkbe.modules.admin.board.application.dto;

/** Sort keys supported by {@code GET /admin/boards/posts}. */
public enum AdminBoardPostSortKey {
  CREATED_AT("createdAt"),
  POST_ID("postId"),
  STATUS("status"),
  TYPE("type"),
  COMMENT_COUNT("commentCount");

  private final String apiValue;

  AdminBoardPostSortKey(String apiValue) {
    this.apiValue = apiValue;
  }

  public static AdminBoardPostSortKey from(String value) {
    if (value == null || value.isBlank()) {
      return CREATED_AT;
    }
    for (AdminBoardPostSortKey sortKey : values()) {
      if (sortKey.apiValue.equals(value)) {
        return sortKey;
      }
    }
    throw new IllegalArgumentException("Unsupported sort value: " + value);
  }
}
