package momzzangseven.mztkbe.modules.post.api.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyPostsCursorResult;

public record GetMyPostsV2Response(
    List<PostListResponse> posts, boolean hasNext, String nextCursor) {

  public static GetMyPostsV2Response from(GetMyPostsCursorResult result) {
    return new GetMyPostsV2Response(
        result.posts().stream().map(PostListResponse::from).toList(),
        result.hasNext(),
        result.nextCursor());
  }
}
