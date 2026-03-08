package momzzangseven.mztkbe.modules.location.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AddressDataTest {

  @Test
  void constructor_shouldThrowWhenAddressNullOrBlank() {
    assertThatThrownBy(() -> new AddressData(null, "04524", "2F"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Address cannot be null or blank");
    assertThatThrownBy(() -> new AddressData("   ", "04524", "2F"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Address cannot be null or blank");
  }

  @Test
  void constructor_shouldNormalizeNullableFields() {
    AddressData data = new AddressData("Seoul", null, null);

    assertThat(data.address()).isEqualTo("Seoul");
    assertThat(data.postalCode()).isEmpty();
    assertThat(data.detailedAddress()).isEmpty();
  }

  @Test
  void record_shouldUseValueEquality() {
    AddressData a = new AddressData("Seoul", "04524", "2F");
    AddressData b = new AddressData("Seoul", "04524", "2F");

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }
}
