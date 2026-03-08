package momzzangseven.mztkbe.modules.web3.transfer.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransferRuntimeConfigTest {

  @Test
  void constructor_acceptsValidConfig() {
    TransferRuntimeConfig config =
        new TransferRuntimeConfig(
            11155111L,
            "0x" + "a".repeat(40),
            30,
            "0x" + "b".repeat(40),
            "0x" + "c".repeat(40),
            "reward-sponsor",
            "kek",
            1_000_000L,
            new BigDecimal("0.05"),
            new BigDecimal("0.01"),
            new BigDecimal("0.02"),
            600,
            "Asia/Seoul",
            7,
            100);

    assertThat(config.chainId()).isEqualTo(11155111L);
    assertThat(config.cleanupBatchSize()).isEqualTo(100);
  }

  @Test
  void constructor_throws_whenCleanupBatchInvalid() {
    assertThatThrownBy(
            () ->
                new TransferRuntimeConfig(
                    11155111L,
                    "0x" + "a".repeat(40),
                    30,
                    "0x" + "b".repeat(40),
                    "0x" + "c".repeat(40),
                    "reward-sponsor",
                    "kek",
                    1_000_000L,
                    new BigDecimal("0.05"),
                    new BigDecimal("0.01"),
                    new BigDecimal("0.02"),
                    600,
                    "Asia/Seoul",
                    7,
                    0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cleanup config is invalid");
  }

  @Test
  void constructor_throws_whenChainIdInvalid() {
    assertThatThrownBy(
            () ->
                new TransferRuntimeConfig(
                    0L,
                    "0x" + "a".repeat(40),
                    30,
                    "0x" + "b".repeat(40),
                    "0x" + "c".repeat(40),
                    "reward-sponsor",
                    "kek",
                    1_000_000L,
                    new BigDecimal("0.05"),
                    new BigDecimal("0.01"),
                    new BigDecimal("0.02"),
                    600,
                    "Asia/Seoul",
                    7,
                    100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("chainId must be positive");
  }

  @Test
  void constructor_throws_whenTokenContractAddressBlank() {
    assertThatThrownBy(
            () ->
                new TransferRuntimeConfig(
                    11155111L,
                    " ",
                    30,
                    "0x" + "b".repeat(40),
                    "0x" + "c".repeat(40),
                    "reward-sponsor",
                    "kek",
                    1_000_000L,
                    new BigDecimal("0.05"),
                    new BigDecimal("0.01"),
                    new BigDecimal("0.02"),
                    600,
                    "Asia/Seoul",
                    7,
                    100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tokenContractAddress is required");
  }

  @Test
  void constructor_throws_whenRetryBackoffInvalid() {
    assertThatThrownBy(
            () ->
                new TransferRuntimeConfig(
                    11155111L,
                    "0x" + "a".repeat(40),
                    0,
                    "0x" + "b".repeat(40),
                    "0x" + "c".repeat(40),
                    "reward-sponsor",
                    "kek",
                    1_000_000L,
                    new BigDecimal("0.05"),
                    new BigDecimal("0.01"),
                    new BigDecimal("0.02"),
                    600,
                    "Asia/Seoul",
                    7,
                    100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retryBackoffSeconds must be positive");
  }

  @Test
  void constructor_throws_whenDelegationAddressBlank() {
    assertThatThrownBy(
            () ->
                new TransferRuntimeConfig(
                    11155111L,
                    "0x" + "a".repeat(40),
                    30,
                    " ",
                    "0x" + "c".repeat(40),
                    "reward-sponsor",
                    "kek",
                    1_000_000L,
                    new BigDecimal("0.05"),
                    new BigDecimal("0.01"),
                    new BigDecimal("0.02"),
                    600,
                    "Asia/Seoul",
                    7,
                    100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("delegation addresses are required");
  }

  @Test
  void constructor_throws_whenSponsorKeySettingsMissing() {
    assertThatThrownBy(
            () ->
                new TransferRuntimeConfig(
                    11155111L,
                    "0x" + "a".repeat(40),
                    30,
                    "0x" + "b".repeat(40),
                    "0x" + "c".repeat(40),
                    " ",
                    "kek",
                    1_000_000L,
                    new BigDecimal("0.05"),
                    new BigDecimal("0.01"),
                    new BigDecimal("0.02"),
                    600,
                    "Asia/Seoul",
                    7,
                    100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sponsor key settings are required");
  }

  @Test
  void constructor_throws_whenSponsorMaxGasLimitInvalid() {
    assertThatThrownBy(
            () ->
                new TransferRuntimeConfig(
                    11155111L,
                    "0x" + "a".repeat(40),
                    30,
                    "0x" + "b".repeat(40),
                    "0x" + "c".repeat(40),
                    "reward-sponsor",
                    "kek",
                    0L,
                    new BigDecimal("0.05"),
                    new BigDecimal("0.01"),
                    new BigDecimal("0.02"),
                    600,
                    "Asia/Seoul",
                    7,
                    100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sponsorMaxGasLimit must be positive");
  }

  @Test
  void constructor_throws_whenSponsorCapNegative() {
    assertThatThrownBy(
            () ->
                new TransferRuntimeConfig(
                    11155111L,
                    "0x" + "a".repeat(40),
                    30,
                    "0x" + "b".repeat(40),
                    "0x" + "c".repeat(40),
                    "reward-sponsor",
                    "kek",
                    1_000_000L,
                    new BigDecimal("-0.01"),
                    new BigDecimal("0.01"),
                    new BigDecimal("0.02"),
                    600,
                    "Asia/Seoul",
                    7,
                    100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sponsor caps must be >= 0");
  }

  @Test
  void constructor_throws_whenAuthorizationTtlInvalid() {
    assertThatThrownBy(
            () ->
                new TransferRuntimeConfig(
                    11155111L,
                    "0x" + "a".repeat(40),
                    30,
                    "0x" + "b".repeat(40),
                    "0x" + "c".repeat(40),
                    "reward-sponsor",
                    "kek",
                    1_000_000L,
                    new BigDecimal("0.05"),
                    new BigDecimal("0.01"),
                    new BigDecimal("0.02"),
                    0,
                    "Asia/Seoul",
                    7,
                    100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("authorizationTtlSeconds must be positive");
  }

  @Test
  void constructor_throws_whenSponsorMaxTransferAmountEthNull() {
    assertThatThrownBy(
            () ->
                new TransferRuntimeConfig(
                    11155111L,
                    "0x" + "a".repeat(40),
                    30,
                    "0x" + "b".repeat(40),
                    "0x" + "c".repeat(40),
                    "reward-sponsor",
                    "kek",
                    1_000_000L,
                    null,
                    new BigDecimal("0.01"),
                    new BigDecimal("0.02"),
                    600,
                    "Asia/Seoul",
                    7,
                    100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sponsor caps must be >= 0");
  }

  @Test
  void constructor_throws_whenSponsorPerTxCapEthNull() {
    assertThatThrownBy(
            () ->
                new TransferRuntimeConfig(
                    11155111L,
                    "0x" + "a".repeat(40),
                    30,
                    "0x" + "b".repeat(40),
                    "0x" + "c".repeat(40),
                    "reward-sponsor",
                    "kek",
                    1_000_000L,
                    new BigDecimal("0.05"),
                    null,
                    new BigDecimal("0.02"),
                    600,
                    "Asia/Seoul",
                    7,
                    100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sponsor caps must be >= 0");
  }

  @Test
  void constructor_throws_whenSponsorPerDayUserCapEthNull() {
    assertThatThrownBy(
            () ->
                new TransferRuntimeConfig(
                    11155111L,
                    "0x" + "a".repeat(40),
                    30,
                    "0x" + "b".repeat(40),
                    "0x" + "c".repeat(40),
                    "reward-sponsor",
                    "kek",
                    1_000_000L,
                    new BigDecimal("0.05"),
                    new BigDecimal("0.01"),
                    null,
                    600,
                    "Asia/Seoul",
                    7,
                    100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sponsor caps must be >= 0");
  }

  @Test
  void constructor_throws_whenCleanupZoneNull() {
    assertThatThrownBy(
            () ->
                new TransferRuntimeConfig(
                    11155111L,
                    "0x" + "a".repeat(40),
                    30,
                    "0x" + "b".repeat(40),
                    "0x" + "c".repeat(40),
                    "reward-sponsor",
                    "kek",
                    1_000_000L,
                    new BigDecimal("0.05"),
                    new BigDecimal("0.01"),
                    new BigDecimal("0.02"),
                    600,
                    null,
                    7,
                    100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cleanup config is invalid");
  }

  @Test
  void constructor_throws_whenCleanupZoneBlank() {
    assertThatThrownBy(
            () ->
                new TransferRuntimeConfig(
                    11155111L,
                    "0x" + "a".repeat(40),
                    30,
                    "0x" + "b".repeat(40),
                    "0x" + "c".repeat(40),
                    "reward-sponsor",
                    "kek",
                    1_000_000L,
                    new BigDecimal("0.05"),
                    new BigDecimal("0.01"),
                    new BigDecimal("0.02"),
                    600,
                    " ",
                    7,
                    100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cleanup config is invalid");
  }
}
