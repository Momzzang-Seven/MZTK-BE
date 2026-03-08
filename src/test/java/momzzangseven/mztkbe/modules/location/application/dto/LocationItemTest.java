package momzzangseven.mztkbe.modules.location.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import org.junit.jupiter.api.Test;

class LocationItemTest {

  @Test
  void from_shouldMapDomainFields() {
    Instant registeredAt = Instant.parse("2026-02-28T10:00:00Z");
    Location location =
        Location.builder()
            .id(10L)
            .userId(1L)
            .locationName("Seoul Gym")
            .postalCode("04524")
            .address("Seoul")
            .detailAddress("2F")
            .coordinate(new GpsCoordinate(37.5665, 126.9780))
            .registeredAt(registeredAt)
            .build();

    LocationItem item = LocationItem.from(location);

    assertThat(item.locationId()).isEqualTo(10L);
    assertThat(item.locationName()).isEqualTo("Seoul Gym");
    assertThat(item.latitude()).isEqualTo(37.5665);
    assertThat(item.longitude()).isEqualTo(126.9780);
    assertThat(item.registeredAt()).isEqualTo(registeredAt);
  }
}
