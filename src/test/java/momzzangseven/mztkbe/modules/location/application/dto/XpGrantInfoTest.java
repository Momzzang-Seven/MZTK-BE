package momzzangseven.mztkbe.modules.location.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class XpGrantInfoTest {

  @Test
  void constructor_shouldKeepFields() {
    XpGrantInfo info = new XpGrantInfo(true, 100, "OK");

    assertThat(info.granted()).isTrue();
    assertThat(info.amount()).isEqualTo(100);
    assertThat(info.message()).isEqualTo("OK");
  }

  @Test
  void record_shouldUseValueEquality() {
    XpGrantInfo a = new XpGrantInfo(false, 0, "DAILY_CAP_REACHED");
    XpGrantInfo b = new XpGrantInfo(false, 0, "DAILY_CAP_REACHED");

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }
}
