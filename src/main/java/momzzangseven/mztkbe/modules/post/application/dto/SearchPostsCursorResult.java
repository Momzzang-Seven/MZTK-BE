package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.List;

public record SearchPostsCursorResult(
    List<PostListResult> posts, boolean hasNext, String nextCursor) {

  public SearchPostsCursorResult {
    posts = posts == null ? List.of() : List.copyOf(posts);
  }
}
