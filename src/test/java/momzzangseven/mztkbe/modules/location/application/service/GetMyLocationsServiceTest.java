package momzzangseven.mztkbe.modules.location.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.location.application.dto.GetMyLocationsResult;
import momzzangseven.mztkbe.modules.location.application.port.out.LoadLocationPort;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetMyLocationsServiceTest {

  @Mock private LoadLocationPort loadLocationPort;

  @InjectMocks private GetMyLocationsService service;

  @Test
  void execute_shouldMapLocationListToItems() {
    when(loadLocationPort.findByUserId(1L))
        .thenReturn(
            List.of(
                Location.builder()
                    .id(10L)
                    .userId(1L)
                    .locationName("Gym")
                    .postalCode("04524")
                    .address("Seoul")
                    .detailAddress("2F")
                    .coordinate(new GpsCoordinate(37.5, 126.9))
                    .registeredAt(Instant.parse("2026-02-28T10:00:00Z"))
                    .build()));

    GetMyLocationsResult result = service.execute(1L);

    assertThat(result.totalCount()).isEqualTo(1);
    assertThat(result.locations().getFirst().locationName()).isEqualTo("Gym");
  }

  @Test
  void execute_shouldReturnEmptyWhenNoLocations() {
    when(loadLocationPort.findByUserId(1L)).thenReturn(List.of());

    GetMyLocationsResult result = service.execute(1L);

    assertThat(result.locations()).isEmpty();
    assertThat(result.totalCount()).isZero();
  }
}
