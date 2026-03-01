package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.post.application.port.out.GrantPostXpPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostXpService unit test")
class PostXpServiceTest {

  @Mock private GrantPostXpPort grantPostXpPort;

  @InjectMocks private PostXpService postXpService;

  @Test
  @DisplayName("returns granted XP from port")
  void grantCreatePostXpSuccess() {
    when(grantPostXpPort.grantCreatePostXp(7L, 99L)).thenReturn(20L);

    Long grantedXp = postXpService.grantCreatePostXp(7L, 99L);

    assertThat(grantedXp).isEqualTo(20L);
    verify(grantPostXpPort).grantCreatePostXp(7L, 99L);
  }

  @Test
  @DisplayName("propagates exceptions from port")
  void grantCreatePostXpRethrowsError() {
    when(grantPostXpPort.grantCreatePostXp(7L, 99L))
        .thenThrow(new IllegalStateException("xp system down"));

    assertThatThrownBy(() -> postXpService.grantCreatePostXp(7L, 99L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("xp system down");

    verify(grantPostXpPort).grantCreatePostXp(7L, 99L);
  }
}
