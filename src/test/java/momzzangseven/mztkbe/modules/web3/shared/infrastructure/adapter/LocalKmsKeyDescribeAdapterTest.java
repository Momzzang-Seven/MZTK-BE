package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link LocalKmsKeyDescribeAdapter} — covers [M-37].
 *
 * <p>The adapter is the {@code !prod} stand-in for {@link KmsKeyDescribeAdapter} and is required to
 * always return {@link KmsKeyState#ENABLED} regardless of input, never throwing. Production code
 * paths cannot reach this class because the {@code @Profile("!prod")} gate excludes it at DI time.
 */
@DisplayName("LocalKmsKeyDescribeAdapter 단위 테스트")
class LocalKmsKeyDescribeAdapterTest {

  private LocalKmsKeyDescribeAdapter adapter;

  @BeforeEach
  void createAdapter() {
    adapter = new LocalKmsKeyDescribeAdapter();
  }

  @ParameterizedTest(name = "describe(\"{0}\") → ENABLED")
  @ValueSource(
      strings = {"any-string", "arn:aws:kms:us-east-1:123456789012:key/abcd1234", "kms-id", "  "})
  @DisplayName("[M-37] describe — 임의 입력에 대해 항상 ENABLED 반환")
  void describe_anyInput_alwaysReturnsEnabled(String kmsKeyId) {
    // when
    KmsKeyState result = adapter.describe(kmsKeyId);

    // then
    assertThat(result).isSameAs(KmsKeyState.ENABLED);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @DisplayName("[M-37] describe — null / 빈 문자열 입력에서도 ENABLED 반환 (예외 없음)")
  void describe_nullOrEmpty_returnsEnabledWithoutThrowing(String kmsKeyId) {
    // when / then
    assertThatNoException().isThrownBy(() -> adapter.describe(kmsKeyId));
    assertThat(adapter.describe(kmsKeyId)).isSameAs(KmsKeyState.ENABLED);
  }

  @Test
  @DisplayName("[M-37] describe — 동일 호출의 결과는 enum identity 보장")
  void describe_repeatedCalls_returnsSameEnumIdentity() {
    // when
    KmsKeyState first = adapter.describe("k1");
    KmsKeyState second = adapter.describe("k2");

    // then — same enum constant (identity)
    assertThat(first).isSameAs(second).isSameAs(KmsKeyState.ENABLED);
  }
}
