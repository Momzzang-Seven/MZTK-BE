package momzzangseven.mztkbe.modules.location.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RegisterLocationResultTest {

  @Test
  void from_shouldMergeItemAndUserId() {
    Instant registeredAt = Instant.parse("2026-02-28T10:00:00Z");
    LocationItem item =
        new LocationItem(10L, "Seoul Gym", "04524", "Seoul", "2F", 37.5665, 126.9780, registeredAt);

    RegisterLocationResult result = RegisterLocationResult.from(item, 1L);

    assertThat(result.locationId()).isEqualTo(10L);
    assertThat(result.userId()).isEqualTo(1L);
    assertThat(result.locationName()).isEqualTo("Seoul Gym");
    assertThat(result.registeredAt()).isEqualTo(registeredAt);
  }
}
