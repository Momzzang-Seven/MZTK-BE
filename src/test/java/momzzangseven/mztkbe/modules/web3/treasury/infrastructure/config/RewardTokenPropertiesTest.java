package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RewardTokenProperties} bean validation — covers [M-131] .. [M-137].
 *
 * <p>Uses the Bean Validation API directly (no Spring context). Each negative case constructs a
 * minimally-invalid instance and asserts the corresponding {@code ConstraintViolation} fires.
 */
@DisplayName("RewardTokenProperties bean validation")
class RewardTokenPropertiesTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void buildValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeFactory() {
    if (factory != null) {
      factory.close();
    }
  }

  /** Builds a fully-populated, valid {@link RewardTokenProperties} instance. */
  private static RewardTokenProperties valid() {
    RewardTokenProperties p = new RewardTokenProperties();
    p.setEnabled(true);
    p.setTokenContractAddress("0x" + "a".repeat(40));
    p.setDecimals(18);

    p.getTreasury().getProvisioning().setEnabled(true);

    p.getPrevalidate().setEthWarningThreshold(new BigDecimal("0.1"));
    p.getPrevalidate().setEthCriticalThreshold(new BigDecimal("0.05"));

    p.getGas().setDefaultGasLimit(21_000L);
    p.getGas().setDefaultMaxPriorityFeePerGasWei(1L);
    p.getGas().setMaxFeeMultiplier(2);

    p.getWorker().setClaimTtlSeconds(60);
    p.getWorker().setReceiptTimeoutSeconds(30);
    p.getWorker().setReceiptPollMinSeconds(1);
    p.getWorker().setReceiptPollMaxSeconds(5);
    p.getWorker().setRetryBackoffSeconds(2);

    return p;
  }

  @Nested
  @DisplayName("A. 정상 경로")
  class HappyPath {

    @Test
    @DisplayName("[M-131] 모든 필드가 채워져 있으면 위반 없음")
    void valid_noViolations() {
      Set<ConstraintViolation<RewardTokenProperties>> violations = validator.validate(valid());

      assertThat(violations).isEmpty();
    }
  }

  @Nested
  @DisplayName("B. 필수 필드 검증")
  class RequiredFields {

    @Test
    @DisplayName("[M-132] enabled가 null이면 ConstraintViolation")
    void enabledNull_violates() {
      RewardTokenProperties p = valid();
      p.setEnabled(null);

      Set<ConstraintViolation<RewardTokenProperties>> violations = validator.validate(p);

      assertThat(violations)
          .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("enabled"));
    }

    @Test
    @DisplayName("[M-133] tokenContractAddress가 blank면 ConstraintViolation")
    void tokenContractAddressBlank_violates() {
      RewardTokenProperties p = valid();
      p.setTokenContractAddress("  ");

      Set<ConstraintViolation<RewardTokenProperties>> violations = validator.validate(p);

      assertThat(violations)
          .anySatisfy(
              v -> assertThat(v.getPropertyPath().toString()).isEqualTo("tokenContractAddress"));
    }

    @Test
    @DisplayName("[M-134a] decimals가 null이면 ConstraintViolation")
    void decimalsNull_violates() {
      RewardTokenProperties p = valid();
      p.setDecimals(null);

      Set<ConstraintViolation<RewardTokenProperties>> violations = validator.validate(p);

      assertThat(violations)
          .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("decimals"));
    }

    @Test
    @DisplayName("[M-134b] decimals가 음수이면 ConstraintViolation")
    void decimalsNegative_violates() {
      RewardTokenProperties p = valid();
      p.setDecimals(-1);

      Set<ConstraintViolation<RewardTokenProperties>> violations = validator.validate(p);

      assertThat(violations)
          .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("decimals"));
    }
  }

  @Nested
  @DisplayName("C. 중첩 클래스 검증 (Prevalidate / Gas / Worker)")
  class NestedValidation {

    @Test
    @DisplayName("[M-135] prevalidate.ethWarningThreshold가 음수이면 ConstraintViolation")
    void prevalidateEthWarningNegative_violates() {
      RewardTokenProperties p = valid();
      p.getPrevalidate().setEthWarningThreshold(new BigDecimal("-1"));

      Set<ConstraintViolation<RewardTokenProperties>> violations = validator.validate(p);

      assertThat(violations)
          .anySatisfy(
              v ->
                  assertThat(v.getPropertyPath().toString())
                      .isEqualTo("prevalidate.ethWarningThreshold"));
    }

    @Test
    @DisplayName("[M-136] gas.defaultGasLimit < 21_000이면 ConstraintViolation")
    void gasDefaultGasLimitTooLow_violates() {
      RewardTokenProperties p = valid();
      p.getGas().setDefaultGasLimit(1L);

      Set<ConstraintViolation<RewardTokenProperties>> violations = validator.validate(p);

      assertThat(violations)
          .anySatisfy(
              v -> assertThat(v.getPropertyPath().toString()).isEqualTo("gas.defaultGasLimit"));
    }

    @Test
    @DisplayName("[M-137] worker.claimTtlSeconds < 1이면 ConstraintViolation")
    void workerClaimTtlSecondsTooLow_violates() {
      RewardTokenProperties p = valid();
      p.getWorker().setClaimTtlSeconds(0);

      Set<ConstraintViolation<RewardTokenProperties>> violations = validator.validate(p);

      assertThat(violations)
          .anySatisfy(
              v -> assertThat(v.getPropertyPath().toString()).isEqualTo("worker.claimTtlSeconds"));
    }

    @Test
    @DisplayName("[M-137b] treasury.provisioning.enabled가 null이면 ConstraintViolation")
    void treasuryProvisioningEnabledNull_violates() {
      RewardTokenProperties p = valid();
      p.getTreasury().getProvisioning().setEnabled(null);

      Set<ConstraintViolation<RewardTokenProperties>> violations = validator.validate(p);

      assertThat(violations)
          .anySatisfy(
              v ->
                  assertThat(v.getPropertyPath().toString())
                      .isEqualTo("treasury.provisioning.enabled"));
    }
  }

  @Nested
  @DisplayName("D. tokenContractAddress null 가드")
  class NullGuard {

    @Test
    @DisplayName("[M-133b] tokenContractAddress가 null이면 @NotBlank ConstraintViolation")
    void tokenContractAddressNull_violates() {
      RewardTokenProperties p = valid();
      p.setTokenContractAddress(null);

      Set<ConstraintViolation<RewardTokenProperties>> violations = validator.validate(p);

      assertThat(violations)
          .anySatisfy(
              v -> assertThat(v.getPropertyPath().toString()).isEqualTo("tokenContractAddress"));
    }
  }
}
