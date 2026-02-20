package momzzangseven.mztkbe.modules.comment.application.dto;

import org.springframework.data.domain.Pageable;

public record GetRepliesQuery(Long parentId, Pageable pageable) {}
