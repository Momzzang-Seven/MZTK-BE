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
 * valued on each shared-module infrastructure adapter — covers [M-36] (shared part).
 *
 * <p>No mocks, no Spring context — purely inspects class-level annotations at compile time. The
 * treasury-module lifecycle adapters ({@code KmsKeyLifecycleAdapter} / {@code
 * LocalKmsKeyLifecycleAdapter}) are covered separately under the treasury module test suite.
 */
@DisplayName("Adapter @Profile 게이팅 테스트")
class AdapterProfileGatingTest {

  @Nested
  @DisplayName("A. prod 프로파일 어댑터")
  class ProdProfileAdapters {

    @Test
    @DisplayName("[M-36] KmsSignerAdapter는 @Profile(\"prod\") 보유")
    void kmsSignerAdapter_annotatedWithProdProfile() {
      Profile annotation = KmsSignerAdapter.class.getAnnotation(Profile.class);

      assertThat(annotation).isNotNull();
      List<String> profileValues = Arrays.asList(annotation.value());
      assertThat(profileValues).containsExactly("prod");
    }

    @Test
    @DisplayName("[M-36] KmsKeyDescribeAdapter는 @Profile(\"prod\") 보유")
    void kmsKeyDescribeAdapter_annotatedWithProdProfile() {
      Profile annotation = KmsKeyDescribeAdapter.class.getAnnotation(Profile.class);

      assertThat(annotation).isNotNull();
      List<String> profileValues = Arrays.asList(annotation.value());
      assertThat(profileValues).containsExactly("prod");
    }
  }

  @Nested
  @DisplayName("B. !prod (local) 프로파일 어댑터")
  class NonProdProfileAdapters {

    @Test
    @DisplayName("[M-36] LocalEcSignerAdapter는 @Profile(\"!prod\") 보유")
    void localEcSignerAdapter_annotatedWithNotProdProfile() {
      Profile annotation = LocalEcSignerAdapter.class.getAnnotation(Profile.class);

      assertThat(annotation).isNotNull();
      List<String> profileValues = Arrays.asList(annotation.value());
      assertThat(profileValues).containsExactly("!prod");
    }

    @Test
    @DisplayName("[M-36] LocalKmsKeyDescribeAdapter는 @Profile(\"!prod\") 보유")
    void localKmsKeyDescribeAdapter_annotatedWithNotProdProfile() {
      Profile annotation = LocalKmsKeyDescribeAdapter.class.getAnnotation(Profile.class);

      assertThat(annotation).isNotNull();
      List<String> profileValues = Arrays.asList(annotation.value());
      assertThat(profileValues).containsExactly("!prod");
    }
  }
}
