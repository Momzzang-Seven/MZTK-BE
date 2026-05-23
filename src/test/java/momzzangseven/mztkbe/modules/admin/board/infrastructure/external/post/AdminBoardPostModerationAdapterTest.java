package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostResult;
import momzzangseven.mztkbe.modules.post.application.port.in.ModerateManagedPostUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminBoardPostModerationAdapter 단위 테스트")
class AdminBoardPostModerationAdapterTest {

  @Mock private ModerateManagedPostUseCase moderateManagedPostUseCase;

  @InjectMocks private AdminBoardPostModerationAdapter adapter;

  @Test
  @DisplayName("게시글 ban은 post 모듈의 non-audited managed moderation port를 호출한다")
  void block_usesManagedModerationPort() {
    ModeratePostCommand command = new ModeratePostCommand(9L, 21L);
    given(moderateManagedPostUseCase.blockManagedPost(command))
        .willReturn(
            new ModeratePostResult(
                21L, true, PostPublicationStatus.VISIBLE, PostModerationStatus.BLOCKED));

    var result = adapter.block(9L, 21L);

    verify(moderateManagedPostUseCase).blockManagedPost(command);
    assertThat(result.postId()).isEqualTo(21L);
    assertThat(result.moderated()).isTrue();
    assertThat(result.publicationStatus()).isEqualTo(AdminBoardPostPublicationStatus.VISIBLE);
    assertThat(result.moderationStatus()).isEqualTo(AdminBoardPostModerationStatus.BLOCKED);
    assertThat(result.publiclyVisible()).isFalse();
  }

  @Test
  @DisplayName("게시글 unblock은 post 모듈의 non-audited managed moderation port를 호출한다")
  void unblock_usesManagedModerationPort() {
    ModeratePostCommand command = new ModeratePostCommand(9L, 21L);
    given(moderateManagedPostUseCase.unblockManagedPost(command))
        .willReturn(
            new ModeratePostResult(
                21L, false, PostPublicationStatus.FAILED, PostModerationStatus.NORMAL));

    var result = adapter.unblock(9L, 21L);

    verify(moderateManagedPostUseCase).unblockManagedPost(command);
    assertThat(result.postId()).isEqualTo(21L);
    assertThat(result.moderated()).isFalse();
    assertThat(result.publicationStatus()).isEqualTo(AdminBoardPostPublicationStatus.FAILED);
    assertThat(result.moderationStatus()).isEqualTo(AdminBoardPostModerationStatus.NORMAL);
    assertThat(result.publiclyVisible()).isFalse();
  }
}
