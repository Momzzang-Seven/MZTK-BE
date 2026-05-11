package momzzangseven.mztkbe.modules.admin.board.application.dto;

/** Sort keys supported by {@code GET /admin/boards/comments}. */
public enum AdminBoardCommentSortKey {
  CREATED_AT("createdAt"),
  COMMENT_ID("commentId");

  private final String apiValue;

  AdminBoardCommentSortKey(String apiValue) {
    this.apiValue = apiValue;
  }

  public static AdminBoardCommentSortKey from(String value) {
    if (value == null || value.isBlank()) {
      return CREATED_AT;
    }
    for (AdminBoardCommentSortKey sortKey : values()) {
      if (sortKey.apiValue.equals(value)) {
        return sortKey;
      }
    }
    throw new IllegalArgumentException("Unsupported sort value: " + value);
  }
}
