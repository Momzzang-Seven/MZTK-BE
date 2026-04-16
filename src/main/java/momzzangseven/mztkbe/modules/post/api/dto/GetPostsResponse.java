package momzzangseven.mztkbe.modules.post.api.dto;

import java.util.List;

/** API response for GET /posts with infinite-scroll pagination metadata. */
public record GetPostsResponse(List<PostListResponse> posts, boolean hasNext) {}
