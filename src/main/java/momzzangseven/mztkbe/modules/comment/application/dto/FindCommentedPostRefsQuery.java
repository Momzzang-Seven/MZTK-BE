package momzzangseven.mztkbe.modules.comment.application.dto;

import java.util.Locale;
import java.util.Set;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;

public record FindCommentedPostRefsQuery(
    Long requesterId, String postType, CursorPageRequest pageRequest) {

  private static final Set<String> SUPPORTED_POST_TYPES = Set.of("FREE", "QUESTION");

  public void validate() {
    if (requesterId == null || requesterId <= 0) {
      throw new IllegalArgumentException("Requester id is required.");
    }
    if (postType == null || postType.isBlank()) {
      throw new IllegalArgumentException("Post type is required.");
    }
    if (!SUPPORTED_POST_TYPES.contains(normalizedPostType())) {
      throw new IllegalArgumentException("Unsupported post type: " + postType);
    }
    if (pageRequest == null) {
      throw new IllegalArgumentException("Page request is required.");
    }
  }

  public String normalizedPostType() {
    return postType.trim().toUpperCase(Locale.ROOT);
  }
}
