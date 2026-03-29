package momzzangseven.mztkbe.modules.comment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.comment.application.port.out.GrantCommentXpPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentXpService unit test")
class CommentXpServiceTest {

  @Mock private GrantCommentXpPort grantCommentXpPort;

  @InjectMocks private CommentXpService commentXpService;

  @Test
  @DisplayName("returns granted XP from port")
  void grantCreateCommentXpSuccess() {
    when(grantCommentXpPort.grantCreateCommentXp(7L, 99L)).thenReturn(1L);

    Long grantedXp = commentXpService.grantCreateCommentXp(7L, 99L);

    assertThat(grantedXp).isEqualTo(1L);
    verify(grantCommentXpPort).grantCreateCommentXp(7L, 99L);
  }

  @Test
  @DisplayName("propagates exceptions from port")
  void grantCreateCommentXpRethrowsError() {
    when(grantCommentXpPort.grantCreateCommentXp(7L, 99L))
        .thenThrow(new IllegalStateException("xp system down"));

    assertThatThrownBy(() -> commentXpService.grantCreateCommentXp(7L, 99L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("xp system down");

    verify(grantCommentXpPort).grantCreateCommentXp(7L, 99L);
  }

  @Test
  @DisplayName("returns zero when XP was not granted because it was already handled")
  void grantCreateCommentXpReturnsZeroWhenAlreadyGranted() {
    when(grantCommentXpPort.grantCreateCommentXp(7L, 99L)).thenReturn(0L);

    Long grantedXp = commentXpService.grantCreateCommentXp(7L, 99L);

    assertThat(grantedXp).isZero();
    verify(grantCommentXpPort).grantCreateCommentXp(7L, 99L);
  }
}
