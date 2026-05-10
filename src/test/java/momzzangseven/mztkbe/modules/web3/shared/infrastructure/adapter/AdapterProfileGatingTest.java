package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Structural reflection tests that verify {@code @ConditionalOnProperty} gating is present and
 * correctly valued on each shared-module infrastructure adapter — covers [M-36] (shared part).
 *
 * <p>No mocks, no Spring context — purely inspects class-level annotations at compile time. The
 * treasury-module lifecycle adapters ({@code KmsKeyLifecycleAdapter} / {@code
 * LocalKmsKeyLifecycleAdapter}) are covered separately under the treasury module test suite.
 */
@DisplayName("Adapter @ConditionalOnProperty 게이팅 테스트")
class AdapterProfileGatingTest {

  private static final String GATING_PROPERTY = "web3.kms.enabled";

  @Nested
  @DisplayName("A. KMS-enabled (web3.kms.enabled=true) 어댑터")
  class KmsEnabledAdapters {

    @Test
    @DisplayName("[M-36] KmsSignerAdapter는 @ConditionalOnProperty(web3.kms.enabled=true) 보유")
    void kmsSignerAdapter_gatedByKmsEnabledTrue() {
      assertGatedOn(KmsSignerAdapter.class, "true", false);
    }

    @Test
    @DisplayName("[M-36] KmsKeyDescribeAdapter는 @ConditionalOnProperty(web3.kms.enabled=true) 보유")
    void kmsKeyDescribeAdapter_gatedByKmsEnabledTrue() {
      assertGatedOn(KmsKeyDescribeAdapter.class, "true", false);
    }
  }

  @Nested
  @DisplayName("B. KMS-disabled / 기본값 (matchIfMissing=true) 어댑터")
  class KmsDisabledAdapters {

    @Test
    @DisplayName(
        "[M-36] LocalEcSignerAdapter는 @ConditionalOnProperty(web3.kms.enabled=false,"
            + " matchIfMissing=true) 보유")
    void localEcSignerAdapter_gatedByKmsEnabledFalseOrMissing() {
      assertGatedOn(LocalEcSignerAdapter.class, "false", true);
    }

    @Test
    @DisplayName(
        "[M-36] LocalKmsKeyDescribeAdapter는 @ConditionalOnProperty(web3.kms.enabled=false,"
            + " matchIfMissing=true) 보유")
    void localKmsKeyDescribeAdapter_gatedByKmsEnabledFalseOrMissing() {
      assertGatedOn(LocalKmsKeyDescribeAdapter.class, "false", true);
    }
  }

  private static void assertGatedOn(
      Class<?> adapterClass, String expectedHavingValue, boolean expectedMatchIfMissing) {
    ConditionalOnProperty annotation = adapterClass.getAnnotation(ConditionalOnProperty.class);

    assertThat(annotation)
        .as("@ConditionalOnProperty must be present on %s", adapterClass.getSimpleName())
        .isNotNull();

    String[] names = annotation.name().length > 0 ? annotation.name() : annotation.value();
    assertThat(names).containsExactly(GATING_PROPERTY);
    assertThat(annotation.havingValue()).isEqualTo(expectedHavingValue);
    assertThat(annotation.matchIfMissing()).isEqualTo(expectedMatchIfMissing);
  }
}
