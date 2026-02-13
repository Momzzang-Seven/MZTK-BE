package momzzangseven.mztkbe.modules.comment.application.dto;

import org.springframework.data.domain.Pageable;

public record GetRootCommentsQuery(Long postId, Pageable pageable) {}
