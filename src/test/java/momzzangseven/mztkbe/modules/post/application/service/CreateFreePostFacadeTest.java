package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostResult;
import momzzangseven.mztkbe.modules.post.application.port.out.GrantPostXpPort;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateFreePostFacade unit test (sequential T1 -> T2 orchestration)")
class CreateFreePostFacadeTest {

  @Mock private CreatePostService createPostService;
  @Mock private GrantPostXpPort grantPostXpPort;

  private CreateFreePostFacade facade;

  @BeforeEach
  void setUp() {
    facade = new CreateFreePostFacade(createPostService, grantPostXpPort);
  }

  private CreatePostCommand command(Long userId) {
    return CreatePostCommand.of(userId, null, "content", PostType.FREE, 0L, null, null);
  }

  @Test
  @DisplayName("saves post first, then grants XP, and reflects granted amount in the result")
  void grantsXpAfterSaveAndReflectsResult() {
    when(createPostService.createFreePost(Mockito.any())).thenReturn(10L);
    when(grantPostXpPort.grantCreatePostXp(7L, 10L)).thenReturn(30L);

    CreatePostResult result = facade.execute(command(7L));

    InOrder inOrder = Mockito.inOrder(createPostService, grantPostXpPort);
    inOrder.verify(createPostService).createFreePost(Mockito.any());
    inOrder.verify(grantPostXpPort).grantCreatePostXp(7L, 10L);

    assertThat(result.postId()).isEqualTo(10L);
    assertThat(result.isXpGranted()).isTrue();
    assertThat(result.grantedXp()).isEqualTo(30L);
    assertThat(result.message()).isEqualTo("게시글 작성 완료! (+30 XP)");
  }

  @Test
  @DisplayName("returns neutral message when no XP is granted (deferred/cap/duplicate)")
  void neutralMessageWhenNoXpGranted() {
    when(createPostService.createFreePost(Mockito.any())).thenReturn(11L);
    when(grantPostXpPort.grantCreatePostXp(1L, 11L)).thenReturn(0L);

    CreatePostResult result = facade.execute(command(1L));

    assertThat(result.postId()).isEqualTo(11L);
    assertThat(result.isXpGranted()).isFalse();
    assertThat(result.grantedXp()).isZero();
    assertThat(result.message()).isEqualTo("게시글 작성 완료");
  }

  @Test
  @DisplayName("does not grant XP when the save transaction fails")
  void doesNotGrantXpWhenSaveFails() {
    when(createPostService.createFreePost(Mockito.any()))
        .thenThrow(new RuntimeException("save failed"));

    assertThatThrownBy(() -> facade.execute(command(4L)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("save failed");

    verifyNoInteractions(grantPostXpPort);
    verify(grantPostXpPort, never()).grantCreatePostXp(Mockito.any(), Mockito.any());
  }
}
