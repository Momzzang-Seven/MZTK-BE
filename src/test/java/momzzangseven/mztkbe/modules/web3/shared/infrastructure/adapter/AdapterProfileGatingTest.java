package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

/**
 * Structural reflection tests that verify {@code @Profile} annotations are present and correctly
 * valued on each infrastructure adapter — M-76 through M-78.
 *
 * <p>No mocks, no Spring context — purely inspects class-level annotations at compile time.
 */
@DisplayName("Adapter @Profile 게이팅 테스트")
class AdapterProfileGatingTest {

  // =========================================================================
  // M-76 — KmsSignerAdapter must carry @Profile("prod")
  // =========================================================================

  @Nested
  @DisplayName("A. KmsSignerAdapter 프로파일 게이팅")
  class KmsSignerAdapterProfile {

    @Test
    @DisplayName("[M-76] KmsSignerAdapter는 @Profile(\"prod\") 보유")
    void kmsSignerAdapter_annotatedWithProdProfile() {
      // given
      Profile annotation = KmsSignerAdapter.class.getAnnotation(Profile.class);

      // then
      assertThat(annotation).isNotNull();
      List<String> profileValues = Arrays.asList(annotation.value());
      assertThat(profileValues).containsExactly("prod");
    }
  }

  // =========================================================================
  // M-77 — KmsKeyDescribeAdapter must carry @Profile("prod")
  // =========================================================================

  @Nested
  @DisplayName("B. KmsKeyDescribeAdapter 프로파일 게이팅")
  class KmsKeyDescribeAdapterProfile {

    @Test
    @DisplayName("[M-77] KmsKeyDescribeAdapter는 @Profile(\"prod\") 보유")
    void kmsKeyDescribeAdapter_annotatedWithProdProfile() {
      // given
      Profile annotation = KmsKeyDescribeAdapter.class.getAnnotation(Profile.class);

      // then
      assertThat(annotation).isNotNull();
      List<String> profileValues = Arrays.asList(annotation.value());
      assertThat(profileValues).containsExactly("prod");
    }
  }

  // =========================================================================
  // M-78 — LocalEcSignerAdapter must carry @Profile("!prod")
  // =========================================================================

  @Nested
  @DisplayName("C. LocalEcSignerAdapter 프로파일 게이팅")
  class LocalEcSignerAdapterProfile {

    @Test
    @DisplayName("[M-78] LocalEcSignerAdapter는 @Profile(\"!prod\") 보유")
    void localEcSignerAdapter_annotatedWithNotProdProfile() {
      // given
      Profile annotation = LocalEcSignerAdapter.class.getAnnotation(Profile.class);

      // then
      assertThat(annotation).isNotNull();
      List<String> profileValues = Arrays.asList(annotation.value());
      assertThat(profileValues).containsExactly("!prod");
    }
  }
}
