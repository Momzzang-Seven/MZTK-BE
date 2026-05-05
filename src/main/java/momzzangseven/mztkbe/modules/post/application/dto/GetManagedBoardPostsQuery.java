package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;

/** Query for admin board post list reads. */
public record GetManagedBoardPostsQuery(String search, PostStatus status) {}
