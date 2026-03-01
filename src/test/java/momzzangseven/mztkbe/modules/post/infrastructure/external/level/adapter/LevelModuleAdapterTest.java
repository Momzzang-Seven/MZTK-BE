package momzzangseven.mztkbe.modules.post.infrastructure.external.level.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LevelModuleAdapter unit test")
class LevelModuleAdapterTest {

  @Mock private GrantXpUseCase grantXpUseCase;

  private final ZoneId appZoneId = ZoneId.of("UTC");

  private LevelModuleAdapter levelModuleAdapter;

  @BeforeEach
  void setUp() {
    levelModuleAdapter = new LevelModuleAdapter(grantXpUseCase, appZoneId);
  }

  @Test
  @DisplayName("builds command with post idempotency key and returns granted xp")
  void grantCreatePostXpBuildsExpectedCommand() {
    LocalDateTime before = LocalDateTime.now(appZoneId);
    when(grantXpUseCase.execute(any(GrantXpCommand.class)))
        .thenReturn(GrantXpResult.granted(25, 3, 1, LocalDate.of(2026, 3, 1)));

    Long result = levelModuleAdapter.grantCreatePostXp(7L, 11L);

    LocalDateTime after = LocalDateTime.now(appZoneId);

    assertThat(result).isEqualTo(25L);

    ArgumentCaptor<GrantXpCommand> commandCaptor = ArgumentCaptor.forClass(GrantXpCommand.class);
    verify(grantXpUseCase).execute(commandCaptor.capture());

    GrantXpCommand command = commandCaptor.getValue();
    assertThat(command.userId()).isEqualTo(7L);
    assertThat(command.xpType()).isEqualTo(XpType.POST);
    assertThat(command.idempotencyKey()).isEqualTo("post:create:11");
    assertThat(command.sourceRef()).isEqualTo("post-creation:11");
    assertThat(command.occurredAt()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
  }

  @Test
  @DisplayName("returns zero when XP was not granted")
  void grantCreatePostXpReturnsZeroForAlreadyGranted() {
    when(grantXpUseCase.execute(any(GrantXpCommand.class)))
        .thenReturn(GrantXpResult.alreadyGranted(3, 3, LocalDate.of(2026, 3, 1)));

    Long result = levelModuleAdapter.grantCreatePostXp(1L, 2L);

    assertThat(result).isZero();
  }
}
