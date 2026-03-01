package momzzangseven.mztkbe.modules.location.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AddressInfoTest {

  @Test
  void of_shouldCreateAddressInfo() {
    AddressInfo info = AddressInfo.of("Seoul", "04524");

    assertThat(info.address()).isEqualTo("Seoul");
    assertThat(info.postalCode()).isEqualTo("04524");
  }

  @Test
  void record_shouldUseValueEquality() {
    AddressInfo a = new AddressInfo("Seoul", "04524");
    AddressInfo b = new AddressInfo("Seoul", "04524");

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }
}
