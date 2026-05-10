package momzzangseven.mztkbe.modules.admin.board.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AdminBoardModerationReasonRequestDTO 단위 테스트")
class AdminBoardModerationReasonRequestDTOTest {

  @Test
  @DisplayName("post ban command 로 변환하면서 reasonDetail 을 trim 한다")
  void toBanPostCommand_trimsReasonDetail() {
    var request =
        new AdminBoardModerationReasonRequestDTO(
            AdminBoardModerationReasonCode.POLICY_VIOLATION, "  policy  ");

    var command = request.toBanPostCommand(9L, 21L);

    assertThat(command.operatorUserId()).isEqualTo(9L);
    assertThat(command.postId()).isEqualTo(21L);
    assertThat(command.reasonCode()).isEqualTo(AdminBoardModerationReasonCode.POLICY_VIOLATION);
    assertThat(command.reasonDetail()).isEqualTo("policy");
  }

  @Test
  @DisplayName("post unblock command 로 변환하면서 blank reasonDetail 은 null 로 정규화한다")
  void toUnblockPostCommand_normalizesBlankReasonDetail() {
    var request =
        new AdminBoardModerationReasonRequestDTO(AdminBoardModerationReasonCode.OTHER, "   ");

    var command = request.toUnblockPostCommand(9L, 21L);

    assertThat(command.operatorUserId()).isEqualTo(9L);
    assertThat(command.postId()).isEqualTo(21L);
    assertThat(command.reasonCode()).isEqualTo(AdminBoardModerationReasonCode.OTHER);
    assertThat(command.reasonDetail()).isNull();
  }

  @Test
  @DisplayName("comment ban command 로 변환한다")
  void toBanCommentCommand_returnsCommentCommand() {
    var request =
        new AdminBoardModerationReasonRequestDTO(AdminBoardModerationReasonCode.SPAM, "ad");

    var command = request.toBanCommentCommand(9L, 31L);

    assertThat(command.operatorUserId()).isEqualTo(9L);
    assertThat(command.commentId()).isEqualTo(31L);
    assertThat(command.reasonCode()).isEqualTo(AdminBoardModerationReasonCode.SPAM);
    assertThat(command.reasonDetail()).isEqualTo("ad");
  }
}
