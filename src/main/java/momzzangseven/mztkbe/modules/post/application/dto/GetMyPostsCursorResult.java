package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.List;

public record GetMyPostsCursorResult(
    List<PostListResult> posts, boolean hasNext, String nextCursor) {

  public GetMyPostsCursorResult {
    posts = posts == null ? List.of() : List.copyOf(posts);
  }
}
