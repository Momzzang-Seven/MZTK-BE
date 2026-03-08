package momzzangseven.mztkbe.modules.web3.shared.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class EvmAddressTest {

  @Test
  void of_normalizesTrimAndLowercase() {
    EvmAddress address = EvmAddress.of("  0x5Aaeb6053f3E94C9b9A09f33669435E7Ef1BeAed  ");

    assertThat(address.value()).isEqualTo("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed");
    assertThat(address.toString()).isEqualTo(address.value());
  }

  @Test
  void of_throws_whenBlank() {
    assertThatThrownBy(() -> EvmAddress.of("  "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("EVM address is required");
  }

  @Test
  void of_throws_whenNull() {
    assertThatThrownBy(() -> EvmAddress.of(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("EVM address is required");
  }

  @Test
  void of_throws_whenInvalidFormat() {
    assertThatThrownBy(() -> EvmAddress.of("0x1234"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("invalid EVM address");
  }
}
