package momzzangseven.mztkbe.modules.location.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import momzzangseven.mztkbe.modules.location.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class XpGrantServiceTest {

  @Mock private GrantXpPort grantXpPort;

  @InjectMocks private XpGrantService service;

  @Test
  void grantXp_shouldReturnGrantedXpOnSuccess() {
    LocationVerification verification = verification(100L);
    when(grantXpPort.grantLocationVerificationXp(verification)).thenReturn(50);

    int granted = service.grantXp(verification);

    assertThat(granted).isEqualTo(50);
  }

  @Test
  void grantXp_shouldPropagateException() {
    LocationVerification verification = verification(101L);
    when(grantXpPort.grantLocationVerificationXp(verification))
        .thenThrow(new RuntimeException("downstream failure"));

    assertThatThrownBy(() -> service.grantXp(verification)).isInstanceOf(RuntimeException.class);
  }

  private LocationVerification verification(Long id) {
    return LocationVerification.builder()
        .id(id)
        .userId(1L)
        .locationId(10L)
        .locationName("Gym")
        .isVerified(true)
        .distance(1.2)
        .registeredCoordinate(new GpsCoordinate(37.5, 126.9))
        .currentCoordinate(new GpsCoordinate(37.50001, 126.90001))
        .verifiedAt(Instant.now())
        .build();
  }
}
