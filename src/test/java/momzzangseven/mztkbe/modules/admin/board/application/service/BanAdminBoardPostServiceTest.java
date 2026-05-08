package momzzangseven.mztkbe.modules.admin.board.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardPostCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.ChangeAdminBoardPostModerationPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostModerationTargetPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.SaveAdminBoardModerationActionPort;
import momzzangseven.mztkbe.modules.admin.board.domain.model.AdminBoardModerationAction;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationExecutionMode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetFlowType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BanAdminBoardPostService 단위 테스트")
class BanAdminBoardPostServiceTest {

  @Mock private ChangeAdminBoardPostModerationPort changeAdminBoardPostModerationPort;
  @Mock private LoadAdminBoardPostModerationTargetPort loadAdminBoardPostModerationTargetPort;
  @Mock private SaveAdminBoardModerationActionPort saveAdminBoardModerationActionPort;
  @Mock private Clock appClock;

  @InjectMocks private BanAdminBoardPostService service;

  @Test
  @DisplayName("게시글 ban은 moderationStatus 를 BLOCKED 로 변경하고 moderation action을 저장한다")
  void execute_blocksPost() {
    BanAdminBoardPostCommand command =
        new BanAdminBoardPostCommand(
            9L, 21L, AdminBoardModerationReasonCode.POLICY_VIOLATION, "policy memo");
    given(changeAdminBoardPostModerationPort.block(9L, 21L))
        .willReturn(
            new ChangeAdminBoardPostModerationPort.AdminBoardPostModerationChangeResult(
                21L,
                true,
                AdminBoardPostPublicationStatus.VISIBLE,
                AdminBoardPostModerationStatus.BLOCKED));
    given(loadAdminBoardPostModerationTargetPort.load(21L))
        .willReturn(
            new LoadAdminBoardPostModerationTargetPort.AdminBoardPostModerationTarget(
                21L, AdminBoardType.FREE, AdminBoardPostStatus.OPEN));
    given(appClock.instant()).willReturn(Instant.parse("2026-05-04T00:00:00Z"));
    given(appClock.getZone()).willReturn(ZoneId.of("UTC"));

    var result = service.execute(command);

    assertThat(result.targetId()).isEqualTo(21L);
    assertThat(result.targetType()).isEqualTo(AdminBoardModerationTargetType.POST);
    assertThat(result.moderated()).isTrue();
    assertThat(result.publicationStatus()).isEqualTo(AdminBoardPostPublicationStatus.VISIBLE);
    assertThat(result.moderationStatus()).isEqualTo(AdminBoardPostModerationStatus.BLOCKED);

    ArgumentCaptor<AdminBoardModerationAction> captor =
        ArgumentCaptor.forClass(AdminBoardModerationAction.class);
    verify(saveAdminBoardModerationActionPort).save(captor.capture());
    assertThat(captor.getValue().getOperatorId()).isEqualTo(9L);
    assertThat(captor.getValue().getTargetType()).isEqualTo(AdminBoardModerationTargetType.POST);
    assertThat(captor.getValue().getTargetId()).isEqualTo(21L);
    assertThat(captor.getValue().getPostId()).isEqualTo(21L);
    assertThat(captor.getValue().getBoardType()).isEqualTo(AdminBoardType.FREE);
    assertThat(captor.getValue().getReasonCode())
        .isEqualTo(AdminBoardModerationReasonCode.POLICY_VIOLATION);
    assertThat(captor.getValue().getReasonDetail()).isEqualTo("policy memo");
    assertThat(captor.getValue().getTargetFlowType())
        .isEqualTo(AdminBoardModerationTargetFlowType.STANDARD);
    assertThat(captor.getValue().getExecutionMode())
        .isEqualTo(AdminBoardModerationExecutionMode.UNKNOWN);
    assertThat(captor.getValue().getCreatedAt())
        .isEqualTo(LocalDateTime.parse("2026-05-04T00:00:00"));
  }

  @Test
  @DisplayName("이미 BLOCKED 인 게시글 ban은 moderated=false 결과를 반환한다")
  void execute_alreadyBlocked_returnsNotModerated() {
    given(changeAdminBoardPostModerationPort.block(9L, 21L))
        .willReturn(
            new ChangeAdminBoardPostModerationPort.AdminBoardPostModerationChangeResult(
                21L,
                false,
                AdminBoardPostPublicationStatus.FAILED,
                AdminBoardPostModerationStatus.BLOCKED));

    var result =
        service.execute(
            new BanAdminBoardPostCommand(
                9L, 21L, AdminBoardModerationReasonCode.POLICY_VIOLATION, null));

    assertThat(result.moderated()).isFalse();
    assertThat(result.publicationStatus()).isEqualTo(AdminBoardPostPublicationStatus.FAILED);
    assertThat(result.moderationStatus()).isEqualTo(AdminBoardPostModerationStatus.BLOCKED);
    verify(loadAdminBoardPostModerationTargetPort, never()).load(org.mockito.Mockito.anyLong());
    verify(saveAdminBoardModerationActionPort, never())
        .save(org.mockito.Mockito.any(AdminBoardModerationAction.class));
  }
}
