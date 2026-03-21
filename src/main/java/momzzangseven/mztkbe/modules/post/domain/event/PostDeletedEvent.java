package momzzangseven.mztkbe.modules.post.domain.event;

import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record PostDeletedEvent(Long postId, PostType postType) {}
