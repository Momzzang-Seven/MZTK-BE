package momzzangseven.mztkbe.modules.web3.challenge.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChallengeConfigTest {

  @Test
  void constructor_storesFields_whenValid() {
    ChallengeConfig config =
        new ChallengeConfig(120, "example.com", "https://example.com", "1", "1");

    assertThat(config.ttlSeconds()).isEqualTo(120);
    assertThat(config.domain()).isEqualTo("example.com");
  }

  @Test
  void constructor_throws_whenTtlNotPositive() {
    assertThatThrownBy(() -> new ChallengeConfig(0, "example.com", "https://example.com", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TTL must be positive");
  }

  @Test
  void constructor_throws_whenDomainBlank() {
    assertThatThrownBy(() -> new ChallengeConfig(120, " ", "https://example.com", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Domain must not be blank");
  }

  @Test
  void constructor_throws_whenDomainNull() {
    assertThatThrownBy(() -> new ChallengeConfig(120, null, "https://example.com", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Domain must not be blank");
  }

  @Test
  void constructor_throws_whenUriBlank() {
    assertThatThrownBy(() -> new ChallengeConfig(120, "example.com", " ", "1", "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("URI must not be blank");
  }

  @Test
  void constructor_throws_whenUriNull() {
    assertThatThrownBy(() -> new ChallengeConfig(120, "example.com", null, "1", "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("URI must not be blank");
  }

  @Test
  void constructor_throws_whenVersionBlank() {
    assertThatThrownBy(
            () -> new ChallengeConfig(120, "example.com", "https://example.com", " ", "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Version must not be blank");
  }

  @Test
  void constructor_throws_whenVersionNull() {
    assertThatThrownBy(
            () -> new ChallengeConfig(120, "example.com", "https://example.com", null, "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Version must not be blank");
  }

  @Test
  void constructor_throws_whenChainIdBlank() {
    assertThatThrownBy(
            () -> new ChallengeConfig(120, "example.com", "https://example.com", "1", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Chain ID must not be blank");
  }

  @Test
  void constructor_throws_whenChainIdNull() {
    assertThatThrownBy(
            () -> new ChallengeConfig(120, "example.com", "https://example.com", "1", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Chain ID must not be blank");
  }
}
