package momzzangseven.mztkbe.modules.admin.board.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import momzzangseven.mztkbe.modules.admin.board.application.dto.UnblockAdminBoardPostCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.ChangeAdminBoardPostModerationPort;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnblockAdminBoardPostService 단위 테스트")
class UnblockAdminBoardPostServiceTest {

  @Mock private ChangeAdminBoardPostModerationPort changeAdminBoardPostModerationPort;

  @InjectMocks private UnblockAdminBoardPostService service;

  @Test
  @DisplayName("게시글 unblock은 moderationStatus 를 NORMAL 로 변경한 결과를 반환한다")
  void execute_unblocksPost() {
    given(changeAdminBoardPostModerationPort.unblock(9L, 21L))
        .willReturn(
            new ChangeAdminBoardPostModerationPort.AdminBoardPostModerationChangeResult(
                21L,
                true,
                AdminBoardPostPublicationStatus.VISIBLE,
                AdminBoardPostModerationStatus.NORMAL));

    var result =
        service.execute(
            new UnblockAdminBoardPostCommand(
                9L, 21L, AdminBoardModerationReasonCode.POLICY_VIOLATION, null));

    assertThat(result.targetId()).isEqualTo(21L);
    assertThat(result.targetType()).isEqualTo(AdminBoardModerationTargetType.POST);
    assertThat(result.moderated()).isTrue();
    assertThat(result.publicationStatus()).isEqualTo(AdminBoardPostPublicationStatus.VISIBLE);
    assertThat(result.moderationStatus()).isEqualTo(AdminBoardPostModerationStatus.NORMAL);
  }

  @Test
  @DisplayName("FAILED + BLOCKED 게시글 unblock은 publicationStatus 를 유지한다")
  void execute_failedBlockedPost_keepsPublicationStatus() {
    given(changeAdminBoardPostModerationPort.unblock(9L, 21L))
        .willReturn(
            new ChangeAdminBoardPostModerationPort.AdminBoardPostModerationChangeResult(
                21L,
                true,
                AdminBoardPostPublicationStatus.FAILED,
                AdminBoardPostModerationStatus.NORMAL));

    var result =
        service.execute(
            new UnblockAdminBoardPostCommand(
                9L, 21L, AdminBoardModerationReasonCode.POLICY_VIOLATION, null));

    assertThat(result.moderated()).isTrue();
    assertThat(result.publicationStatus()).isEqualTo(AdminBoardPostPublicationStatus.FAILED);
    assertThat(result.moderationStatus()).isEqualTo(AdminBoardPostModerationStatus.NORMAL);
  }

  @Test
  @DisplayName("이미 NORMAL 인 게시글 unblock은 moderated=false 결과를 반환한다")
  void execute_alreadyNormal_returnsNotModerated() {
    given(changeAdminBoardPostModerationPort.unblock(9L, 21L))
        .willReturn(
            new ChangeAdminBoardPostModerationPort.AdminBoardPostModerationChangeResult(
                21L,
                false,
                AdminBoardPostPublicationStatus.FAILED,
                AdminBoardPostModerationStatus.NORMAL));

    var result =
        service.execute(
            new UnblockAdminBoardPostCommand(
                9L, 21L, AdminBoardModerationReasonCode.POLICY_VIOLATION, null));

    assertThat(result.moderated()).isFalse();
    assertThat(result.publicationStatus()).isEqualTo(AdminBoardPostPublicationStatus.FAILED);
    assertThat(result.moderationStatus()).isEqualTo(AdminBoardPostModerationStatus.NORMAL);
  }
}
