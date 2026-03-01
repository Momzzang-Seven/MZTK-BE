package momzzangseven.mztkbe.modules.location.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoordinatesInfoTest {

  @Test
  void of_shouldCreateCoordinates() {
    CoordinatesInfo info = CoordinatesInfo.of(37.5665, 126.9780);

    assertThat(info.latitude()).isEqualTo(37.5665);
    assertThat(info.longitude()).isEqualTo(126.9780);
  }

  @Test
  void constructor_shouldPreserveBoundaryValues() {
    CoordinatesInfo info = new CoordinatesInfo(-90.0, 180.0);

    assertThat(info.latitude()).isEqualTo(-90.0);
    assertThat(info.longitude()).isEqualTo(180.0);
  }
}
