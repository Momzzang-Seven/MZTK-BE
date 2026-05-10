package momzzangseven.mztkbe.modules.admin.user.application.dto;

/** Supported sort keys for {@code GET /admin/users}. */
public enum AdminUserSortKey {
  JOINED_AT("joinedAt"),
  USER_ID("userId"),
  NICKNAME("nickname"),
  ROLE("role"),
  STATUS("status"),
  POST_COUNT("postCount"),
  COMMENT_COUNT("commentCount");

  private final String queryValue;

  AdminUserSortKey(String queryValue) {
    this.queryValue = queryValue;
  }

  public static AdminUserSortKey from(String queryValue) {
    if (queryValue == null) {
      return JOINED_AT;
    }
    for (AdminUserSortKey value : values()) {
      if (value.queryValue.equals(queryValue)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Unsupported sort value: " + queryValue);
  }
}
