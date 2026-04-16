package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.List;

/** Application result for post list/search queries with minimal pagination metadata. */
public record SearchPostsResult(List<PostListResult> posts, boolean hasNext) {}
