package momzzangseven.mztkbe.modules.admin.board.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardModerationResult;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;

/**
 * Response DTO for admin board moderation APIs.
 *
 * <p>Post moderation responses include post state fields. Comment moderation responses do not carry
 * post state, so {@code publicationStatus}, {@code moderationStatus}, and {@code publiclyVisible}
 * are omitted by {@link JsonInclude.Include#NON_NULL}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminBoardModerationResponseDTO(
    Long targetId,
    AdminBoardModerationTargetType targetType,
    AdminBoardModerationReasonCode reasonCode,
    boolean moderated,
    AdminBoardPostPublicationStatus publicationStatus,
    AdminBoardPostModerationStatus moderationStatus,
    Boolean publiclyVisible) {

  public static AdminBoardModerationResponseDTO from(AdminBoardModerationResult result) {
    return new AdminBoardModerationResponseDTO(
        result.targetId(),
        result.targetType(),
        result.reasonCode(),
        result.moderated(),
        result.publicationStatus(),
        result.moderationStatus(),
        result.publiclyVisible());
  }
}
