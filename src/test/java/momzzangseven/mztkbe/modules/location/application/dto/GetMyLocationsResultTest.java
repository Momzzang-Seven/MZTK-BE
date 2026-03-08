package momzzangseven.mztkbe.modules.location.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GetMyLocationsResultTest {

  @Test
  void from_shouldSetTotalCountFromListSize() {
    List<LocationItem> items =
        List.of(
            new LocationItem(1L, "A", "1", "addr1", "d1", 37.0, 127.0, Instant.now()),
            new LocationItem(2L, "B", "2", "addr2", "d2", 38.0, 128.0, Instant.now()));

    GetMyLocationsResult result = GetMyLocationsResult.from(items);

    assertThat(result.locations()).containsExactlyElementsOf(items);
    assertThat(result.totalCount()).isEqualTo(2);
  }

  @Test
  void empty_shouldReturnNoLocations() {
    GetMyLocationsResult result = GetMyLocationsResult.empty();

    assertThat(result.locations()).isEmpty();
    assertThat(result.totalCount()).isZero();
  }
}
