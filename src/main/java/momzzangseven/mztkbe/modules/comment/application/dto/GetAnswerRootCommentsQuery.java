package momzzangseven.mztkbe.modules.comment.application.dto;

import org.springframework.data.domain.Pageable;

public record GetAnswerRootCommentsQuery(Long answerId, Long requesterUserId, Pageable pageable) {}
