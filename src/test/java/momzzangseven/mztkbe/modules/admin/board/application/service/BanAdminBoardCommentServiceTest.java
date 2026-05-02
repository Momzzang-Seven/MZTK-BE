package momzzangseven.mztkbe.modules.admin.board.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardCommentCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.BanAdminBoardCommentPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostModerationTargetPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.SaveAdminBoardModerationActionPort;
import momzzangseven.mztkbe.modules.admin.board.domain.model.AdminBoardModerationAction;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BanAdminBoardCommentService 단위 테스트")
class BanAdminBoardCommentServiceTest {

  @Mock private BanAdminBoardCommentPort banAdminBoardCommentPort;
  @Mock private LoadAdminBoardPostModerationTargetPort loadAdminBoardPostModerationTargetPort;
  @Mock private SaveAdminBoardModerationActionPort saveAdminBoardModerationActionPort;

  @InjectMocks private BanAdminBoardCommentService service;

  @Test
  @DisplayName("댓글 ban 성공 시 soft delete 결과와 moderation action을 저장한다")
  void execute_commentBan_savesModerationAction() {
    BanAdminBoardCommentCommand command =
        new BanAdminBoardCommentCommand(9L, 31L, AdminBoardModerationReasonCode.SPAM, "ad comment");
    given(banAdminBoardCommentPort.ban(31L))
        .willReturn(new BanAdminBoardCommentPort.BanAdminBoardCommentResult(31L, 21L, true));
    given(loadAdminBoardPostModerationTargetPort.load(21L))
        .willReturn(
            new LoadAdminBoardPostModerationTargetPort.AdminBoardPostModerationTarget(
                21L, AdminBoardType.FREE, PostStatus.OPEN));

    var result = service.execute(command);

    assertThat(result.targetId()).isEqualTo(31L);
    assertThat(result.targetType()).isEqualTo(AdminBoardModerationTargetType.COMMENT);
    assertThat(result.reasonCode()).isEqualTo(AdminBoardModerationReasonCode.SPAM);
    assertThat(result.moderated()).isTrue();

    ArgumentCaptor<AdminBoardModerationAction> captor =
        ArgumentCaptor.forClass(AdminBoardModerationAction.class);
    verify(saveAdminBoardModerationActionPort).save(captor.capture());
    assertThat(captor.getValue().getOperatorId()).isEqualTo(9L);
    assertThat(captor.getValue().getTargetType()).isEqualTo(AdminBoardModerationTargetType.COMMENT);
    assertThat(captor.getValue().getTargetId()).isEqualTo(31L);
    assertThat(captor.getValue().getPostId()).isEqualTo(21L);
    assertThat(captor.getValue().getBoardType()).isEqualTo(AdminBoardType.FREE);
    assertThat(captor.getValue().getReasonCode()).isEqualTo(AdminBoardModerationReasonCode.SPAM);
    assertThat(captor.getValue().getReasonDetail()).isEqualTo("ad comment");
  }

  @Test
  @DisplayName("이미 삭제된 댓글 ban은 idempotent 성공으로 반환하되 성공 moderation action은 저장하지 않는다")
  void execute_alreadyDeletedComment_doesNotSaveModerationAction() {
    BanAdminBoardCommentCommand command =
        new BanAdminBoardCommentCommand(9L, 31L, AdminBoardModerationReasonCode.OTHER, null);
    given(banAdminBoardCommentPort.ban(31L))
        .willReturn(new BanAdminBoardCommentPort.BanAdminBoardCommentResult(31L, 21L, false));

    var result = service.execute(command);

    assertThat(result.targetId()).isEqualTo(31L);
    assertThat(result.moderated()).isFalse();
    verify(loadAdminBoardPostModerationTargetPort, never()).load(org.mockito.Mockito.anyLong());
    verify(saveAdminBoardModerationActionPort, never())
        .save(org.mockito.Mockito.any(AdminBoardModerationAction.class));
  }
}
