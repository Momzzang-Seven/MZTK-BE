package momzzangseven.mztkbe.modules.location.infrastructure.external.level.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrantXpAdapterTest {

  @Mock private GrantXpUseCase grantXpUseCase;

  private GrantXpAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new GrantXpAdapter(grantXpUseCase, ZoneId.of("Asia/Seoul"));
  }

  @Test
  void grantLocationVerificationXp_shouldBuildWorkoutCommandAndReturnGrantedXp() {
    LocationVerification verification =
        verification(1L, 10L, Instant.parse("2026-02-28T01:00:00Z"));
    when(grantXpUseCase.execute(any()))
        .thenReturn(GrantXpResult.granted(30, 3, 1, java.time.LocalDate.of(2026, 2, 28)));

    int granted = adapter.grantLocationVerificationXp(verification);

    assertThat(granted).isEqualTo(30);

    ArgumentCaptor<GrantXpCommand> captor = ArgumentCaptor.forClass(GrantXpCommand.class);
    verify(grantXpUseCase).execute(captor.capture());

    GrantXpCommand command = captor.getValue();
    String expectedDay =
        java.time.LocalDateTime.ofInstant(verification.getVerifiedAt(), ZoneId.of("Asia/Seoul"))
            .format(DateTimeFormatter.BASIC_ISO_DATE);

    assertThat(command.userId()).isEqualTo(1L);
    assertThat(command.xpType()).isEqualTo(XpType.WORKOUT);
    assertThat(command.idempotencyKey()).isEqualTo("workout:location-verify:1:10:" + expectedDay);
    assertThat(command.sourceRef()).isEqualTo("location-verification:10");
  }

  @Test
  void grantLocationVerificationXp_shouldReturnZeroWhenNotGranted() {
    LocationVerification verification =
        verification(1L, 10L, Instant.parse("2026-02-28T01:00:00Z"));
    when(grantXpUseCase.execute(any()))
        .thenReturn(GrantXpResult.dailyCapReached(1, 1, java.time.LocalDate.of(2026, 2, 28)));

    int granted = adapter.grantLocationVerificationXp(verification);

    assertThat(granted).isZero();
  }

  private LocationVerification verification(Long userId, Long locationId, Instant verifiedAt) {
    return LocationVerification.builder()
        .id(100L)
        .userId(userId)
        .locationId(locationId)
        .locationName("Gym")
        .isVerified(true)
        .distance(0.2)
        .registeredCoordinate(new GpsCoordinate(37.5, 126.9))
        .currentCoordinate(new GpsCoordinate(37.50001, 126.90001))
        .verifiedAt(verifiedAt)
        .build();
  }
}
