package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Unit tests for {@link LocalKmsKeyLifecycleAdapter} — covers [M-107] and [M-36] (kms-disabled
 * gate).
 *
 * <p>The adapter is intentionally inert in non-prod profiles: every method must throw {@code
 * UnsupportedOperationException} so that a stray call (e.g. via misconfigured tests) surfaces as
 * HTTP 500 instead of silently no-op'ing.
 */
@DisplayName("LocalKmsKeyLifecycleAdapter 단위 테스트")
class LocalKmsKeyLifecycleAdapterTest {

  private final LocalKmsKeyLifecycleAdapter adapter = new LocalKmsKeyLifecycleAdapter();

  @Nested
  @DisplayName("A. 모든 메서드는 UnsupportedOperationException 발생")
  class AllMethodsUnsupported {

    @Test
    @DisplayName("[M-107] createKey")
    void createKey_throwsUnsupported() {
      assertThatThrownBy(adapter::createKey)
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("createKey");
    }

    @Test
    @DisplayName("[M-107] getParametersForImport")
    void getParametersForImport_throwsUnsupported() {
      assertThatThrownBy(() -> adapter.getParametersForImport("k"))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("getParametersForImport");
    }

    @Test
    @DisplayName("[M-107] importKeyMaterial")
    void importKeyMaterial_throwsUnsupported() {
      assertThatThrownBy(() -> adapter.importKeyMaterial("k", new byte[0], new byte[0]))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("importKeyMaterial");
    }

    @Test
    @DisplayName("[M-107] createAlias")
    void createAlias_throwsUnsupported() {
      assertThatThrownBy(() -> adapter.createAlias("a", "k"))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("createAlias");
    }

    @Test
    @DisplayName("[M-107] updateAlias")
    void updateAlias_throwsUnsupported() {
      assertThatThrownBy(() -> adapter.updateAlias("a", "k"))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("updateAlias");
    }

    @Test
    @DisplayName("[M-107] describeAliasTarget")
    void describeAliasTarget_throwsUnsupported() {
      assertThatThrownBy(() -> adapter.describeAliasTarget("a"))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("describeAliasTarget");
    }

    @Test
    @DisplayName("[M-107] disableKey")
    void disableKey_throwsUnsupported() {
      assertThatThrownBy(() -> adapter.disableKey("k"))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("disableKey");
    }

    @Test
    @DisplayName("[M-107] scheduleKeyDeletion")
    void scheduleKeyDeletion_throwsUnsupported() {
      assertThatThrownBy(() -> adapter.scheduleKeyDeletion("k", 7))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("scheduleKeyDeletion");
    }
  }

  @Nested
  @DisplayName("B. @ConditionalOnProperty gating ([M-36])")
  class ProfileGating {

    @Test
    @DisplayName(
        "[M-36] LocalKmsKeyLifecycleAdapter는 @ConditionalOnProperty(web3.kms.enabled=false,"
            + " matchIfMissing=true) 보유")
    void adapter_isKmsDisabledGated() {
      ConditionalOnProperty annotation =
          LocalKmsKeyLifecycleAdapter.class.getAnnotation(ConditionalOnProperty.class);

      assertThat(annotation).isNotNull();
      String[] names = annotation.name().length > 0 ? annotation.name() : annotation.value();
      assertThat(names).containsExactly("web3.kms.enabled");
      assertThat(annotation.havingValue()).isEqualTo("false");
      assertThat(annotation.matchIfMissing()).isTrue();
    }
  }
}
