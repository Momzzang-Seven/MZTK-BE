package momzzangseven.mztkbe.modules.post.api.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.SearchPostsCursorResult;

public record GetPostsV2Response(List<PostListResponse> posts, boolean hasNext, String nextCursor) {

  public static GetPostsV2Response from(SearchPostsCursorResult result) {
    return new GetPostsV2Response(
        result.posts().stream().map(PostListResponse::from).toList(),
        result.hasNext(),
        result.nextCursor());
  }
}
