package momzzangseven.mztkbe.modules.admin.board.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardPostCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostModerationTargetPort;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("BanAdminBoardPostService 단위 테스트")
class BanAdminBoardPostServiceTest {

  @Mock private LoadAdminBoardPostModerationTargetPort loadAdminBoardPostModerationTargetPort;

  @InjectMocks private BanAdminBoardPostService service;

  @Test
  @DisplayName("FREE 게시글 ban은 정책 미확정으로 409를 반환한다")
  void execute_freePost_returnsConflict() {
    given(loadAdminBoardPostModerationTargetPort.load(21L)).willReturn(target(AdminBoardType.FREE));

    assertThatThrownBy(
            () ->
                service.execute(
                    new BanAdminBoardPostCommand(
                        9L, 21L, AdminBoardModerationReasonCode.POLICY_VIOLATION, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getHttpStatus())
                    .isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  @DisplayName("QUESTION 게시글 ban은 Web3 Q&A 정책 미확정으로 409를 반환한다")
  void execute_questionPost_returnsConflict() {
    given(loadAdminBoardPostModerationTargetPort.load(21L))
        .willReturn(target(AdminBoardType.QUESTION));

    assertThatThrownBy(
            () ->
                service.execute(
                    new BanAdminBoardPostCommand(
                        9L, 21L, AdminBoardModerationReasonCode.POLICY_VIOLATION, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getHttpStatus())
                    .isEqualTo(HttpStatus.CONFLICT));
  }

  private LoadAdminBoardPostModerationTargetPort.AdminBoardPostModerationTarget target(
      AdminBoardType boardType) {
    return new LoadAdminBoardPostModerationTargetPort.AdminBoardPostModerationTarget(
        21L, boardType, PostStatus.OPEN);
  }
}
