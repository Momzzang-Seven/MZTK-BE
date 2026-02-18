package momzzangseven.mztkbe.modules.post.domain.event;

import momzzangseven.mztkbe.modules.post.domain.model.PostType;

/** 게시글이 생성되었음을 알리는 이벤트 (레벨 모듈 등 타 모듈은 이 이벤트를 구독하여 로직을 수행함) */
public record PostCreatedEvent(Long userId, Long postId, PostType type) {}
