package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;

public record PostLikeResult(
    PostLikeTargetType targetType, Long targetId, boolean liked, long likeCount) {}
