package momzzangseven.mztkbe.modules.location.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import org.junit.jupiter.api.Test;

class DeleteLocationResultTest {

  @Test
  void from_shouldMapIdAndNameAndSetDeletionTime() {
    Location location =
        Location.builder()
            .id(5L)
            .userId(1L)
            .locationName("Gym")
            .postalCode("04524")
            .address("Seoul")
            .detailAddress("2F")
            .coordinate(new GpsCoordinate(37.5, 126.9))
            .registeredAt(Instant.now())
            .build();

    Instant before = Instant.now();
    DeleteLocationResult result = DeleteLocationResult.from(location);
    Instant after = Instant.now();

    assertThat(result.locationId()).isEqualTo(5L);
    assertThat(result.locationName()).isEqualTo("Gym");
    assertThat(result.deletedAt()).isBetween(before, after);
  }
}
