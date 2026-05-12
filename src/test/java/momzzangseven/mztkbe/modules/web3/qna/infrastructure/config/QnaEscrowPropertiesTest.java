package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QnaEscrowProperties} — verifies default field values and JSR-380 constraint
 * behavior. Covers test cases P-201 .. P-212 (Commit 1, Groups A/B/C).
 */
@DisplayName("QnaEscrowProperties 단위 테스트")
class QnaEscrowPropertiesTest {

  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  // =========================================================================
  // Section A — 기본값 검증
  // =========================================================================

  @Nested
  @DisplayName("A. 기본값 검증")
  class Defaults {

    @Test
    @DisplayName("[P-201] defaults — eip712DomainName 기본값 'QnAEscrow'")
    void defaults_eip712DomainName_isQnAEscrow() {
      // given
      QnaEscrowProperties bean = new QnaEscrowProperties();
      bean.setQnaContractAddress("0xDEAD");

      // then
      assertThat(bean.getEip712DomainName()).isEqualTo("QnAEscrow");
    }

    @Test
    @DisplayName("[P-202] defaults — eip712DomainVersion 기본값 '1'")
    void defaults_eip712DomainVersion_isOne() {
      // given
      QnaEscrowProperties bean = new QnaEscrowProperties();
      bean.setQnaContractAddress("0xDEAD");

      // then
      assertThat(bean.getEip712DomainVersion()).isEqualTo("1");
    }

    @Test
    @DisplayName("[P-203] defaults — signedAtSkewSeconds 기본값 0")
    void defaults_signedAtSkewSeconds_isZero() {
      // given
      QnaEscrowProperties bean = new QnaEscrowProperties();
      bean.setQnaContractAddress("0xDEAD");

      // then
      assertThat(bean.getSignedAtSkewSeconds()).isEqualTo(0);
    }

    @Test
    @DisplayName("[P-204] defaults — sigValidityDuration 기본값 900")
    void defaults_sigValidityDuration_is900() {
      // given
      QnaEscrowProperties bean = new QnaEscrowProperties();
      bean.setQnaContractAddress("0xDEAD");

      // then
      assertThat(bean.getSigValidityDuration()).isEqualTo(900);
    }
  }

  // =========================================================================
  // Section B — 제약 위반 검증
  // =========================================================================

  @Nested
  @DisplayName("B. 제약 위반 검증")
  class ConstraintViolations {

    @Test
    @DisplayName("[P-205] eip712DomainName 공백 → 위반 발생")
    void validate_blankEip712DomainName_producesViolation() {
      // given
      QnaEscrowProperties bean = new QnaEscrowProperties();
      bean.setQnaContractAddress("0x000000000000000000000000000000000000dead");
      bean.setEip712DomainName("   ");

      // when
      Set<ConstraintViolation<QnaEscrowProperties>> violations = validator.validate(bean);

      // then
      assertThat(violations).isNotEmpty();
      assertThat(violations)
          .anyMatch(v -> v.getPropertyPath().toString().equals("eip712DomainName"));
    }

    @Test
    @DisplayName("[P-206] eip712DomainVersion 공백 → 위반 발생")
    void validate_blankEip712DomainVersion_producesViolation() {
      // given
      QnaEscrowProperties bean = new QnaEscrowProperties();
      bean.setQnaContractAddress("0x000000000000000000000000000000000000dead");
      bean.setEip712DomainVersion("");

      // when
      Set<ConstraintViolation<QnaEscrowProperties>> violations = validator.validate(bean);

      // then
      assertThat(violations).isNotEmpty();
      assertThat(violations)
          .anyMatch(v -> v.getPropertyPath().toString().equals("eip712DomainVersion"));
    }

    @Test
    @DisplayName("[P-207] signedAtSkewSeconds 음수 → 위반 발생")
    void validate_negativeSignedAtSkewSeconds_producesViolation() {
      // given
      QnaEscrowProperties bean = new QnaEscrowProperties();
      bean.setQnaContractAddress("0x000000000000000000000000000000000000dead");
      bean.setSignedAtSkewSeconds(-1);

      // when
      Set<ConstraintViolation<QnaEscrowProperties>> violations = validator.validate(bean);

      // then
      assertThat(violations).isNotEmpty();
      assertThat(violations)
          .anyMatch(v -> v.getPropertyPath().toString().equals("signedAtSkewSeconds"));
    }

    @Test
    @DisplayName("[P-208] sigValidityDuration < 60 → 위반 발생")
    void validate_sigValidityDurationBelowMin_producesViolation() {
      // given
      QnaEscrowProperties bean = new QnaEscrowProperties();
      bean.setQnaContractAddress("0x000000000000000000000000000000000000dead");
      bean.setSigValidityDuration(59);

      // when
      Set<ConstraintViolation<QnaEscrowProperties>> violations = validator.validate(bean);

      // then
      assertThat(violations).isNotEmpty();
      assertThat(violations)
          .anyMatch(v -> v.getPropertyPath().toString().equals("sigValidityDuration"));
    }

    @Test
    @DisplayName("[P-209] sigValidityDuration > 3600 → 위반 발생")
    void validate_sigValidityDurationAboveMax_producesViolation() {
      // given
      QnaEscrowProperties bean = new QnaEscrowProperties();
      bean.setQnaContractAddress("0x000000000000000000000000000000000000dead");
      bean.setSigValidityDuration(3601);

      // when
      Set<ConstraintViolation<QnaEscrowProperties>> violations = validator.validate(bean);

      // then
      assertThat(violations).isNotEmpty();
      assertThat(violations)
          .anyMatch(v -> v.getPropertyPath().toString().equals("sigValidityDuration"));
    }
  }

  // =========================================================================
  // Section C — 경계값 및 정상 경로
  // =========================================================================

  @Nested
  @DisplayName("C. 경계값 및 정상 경로")
  class BoundariesAndHappyPath {

    @Test
    @DisplayName("[P-210] sigValidityDuration = 60 → 위반 없음 (하한 경계)")
    void validate_sigValidityDurationAtMin_noViolation() {
      // given
      QnaEscrowProperties bean = new QnaEscrowProperties();
      bean.setQnaContractAddress("0x000000000000000000000000000000000000dead");
      bean.setSigValidityDuration(60);

      // when
      Set<ConstraintViolation<QnaEscrowProperties>> violations = validator.validate(bean);

      // then
      assertThat(violations)
          .noneMatch(v -> v.getPropertyPath().toString().equals("sigValidityDuration"));
    }

    @Test
    @DisplayName("[P-211] sigValidityDuration = 3600 → 위반 없음 (상한 경계)")
    void validate_sigValidityDurationAtMax_noViolation() {
      // given
      QnaEscrowProperties bean = new QnaEscrowProperties();
      bean.setQnaContractAddress("0x000000000000000000000000000000000000dead");
      bean.setSigValidityDuration(3600);

      // when
      Set<ConstraintViolation<QnaEscrowProperties>> violations = validator.validate(bean);

      // then
      assertThat(violations)
          .noneMatch(v -> v.getPropertyPath().toString().equals("sigValidityDuration"));
    }

    @Test
    @DisplayName("[P-212] 전체 기본값 + 유효한 qnaContractAddress → 위반 없음 (happy path)")
    void validate_allDefaults_withValidContractAddress_noViolations() {
      // given
      QnaEscrowProperties bean = new QnaEscrowProperties();
      bean.setQnaContractAddress("0xABCDEF1234567890ABCDEF1234567890ABCDEF12");

      // when
      Set<ConstraintViolation<QnaEscrowProperties>> violations = validator.validate(bean);

      // then
      assertThat(violations).isEmpty();
    }
  }
}
