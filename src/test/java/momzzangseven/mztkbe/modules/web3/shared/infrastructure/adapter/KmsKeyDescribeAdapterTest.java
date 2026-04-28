package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.global.error.web3.KmsKeyDescribeFailedException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.NotFoundException;

/**
 * Unit tests for {@link KmsKeyDescribeAdapter} — M-61 through M-66.
 *
 * <p>Uses {@link MockitoExtension} with a mocked {@link KmsClient}. Adapter is instantiated
 * directly via constructor injection (no Spring context).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KmsKeyDescribeAdapter 단위 테스트")
class KmsKeyDescribeAdapterTest {

  private static final String KEY_ARN = "arn:aws:kms:us-east-1:123456789012:key/test";
  private static final String SPECIFIC_KEY_ARN =
      "arn:aws:kms:us-east-1:999999999999:key/specific-id";

  @Mock private KmsClient kmsClient;

  /** Builds a {@link DescribeKeyResponse} stub with the given AWS {@link KeyState}. */
  private static DescribeKeyResponse stubResponse(KeyState keyState) {
    return DescribeKeyResponse.builder()
        .keyMetadata(KeyMetadata.builder().keyState(keyState).build())
        .build();
  }

  // =========================================================================
  // A. 정상 경로 — KeyState 매핑
  // =========================================================================

  @Nested
  @DisplayName("A. KeyState 매핑 (happy-path)")
  class KeyStateMapping {

    @Test
    @DisplayName("[M-61] describe — KeyState.ENABLED → KmsKeyState.ENABLED")
    void describe_enabledKeyState_returnsEnabled() {
      // given
      when(kmsClient.describeKey(any(DescribeKeyRequest.class)))
          .thenReturn(stubResponse(KeyState.ENABLED));
      KmsKeyDescribeAdapter adapter = new KmsKeyDescribeAdapter(kmsClient);

      // when
      KmsKeyState result = adapter.describe(KEY_ARN);

      // then
      assertThat(result).isEqualTo(KmsKeyState.ENABLED);
    }

    @ParameterizedTest(name = "[M-62] AWS KeyState={0} → KmsKeyState={1}")
    @CsvSource({
      "ENABLED, ENABLED",
      "DISABLED, DISABLED",
      "PENDING_DELETION, PENDING_DELETION",
      "PENDING_IMPORT, PENDING_IMPORT"
    })
    @DisplayName("[M-62] describe — 4개 명시적 매핑 값 (parameterized)")
    void describe_explicitKeyStateMappings_returnsCorrectKmsKeyState(
        String awsStateName, String expectedStateName) {
      // given
      KeyState awsState = KeyState.valueOf(awsStateName);
      KmsKeyState expectedState = KmsKeyState.valueOf(expectedStateName);
      when(kmsClient.describeKey(any(DescribeKeyRequest.class))).thenReturn(stubResponse(awsState));
      KmsKeyDescribeAdapter adapter = new KmsKeyDescribeAdapter(kmsClient);

      // when
      KmsKeyState result = adapter.describe(KEY_ARN);

      // then
      assertThat(result).isSameAs(expectedState);
    }

    @Test
    @DisplayName(
        "[M-63] describe — 미정의 KeyState (CREATING) → KmsKeyState.UNAVAILABLE (fail-closed)")
    void describe_unmappedKeyState_returnsUnavailable() {
      // given
      when(kmsClient.describeKey(any(DescribeKeyRequest.class)))
          .thenReturn(stubResponse(KeyState.CREATING));
      KmsKeyDescribeAdapter adapter = new KmsKeyDescribeAdapter(kmsClient);

      // when
      KmsKeyState result = adapter.describe(KEY_ARN);

      // then
      assertThat(result).isEqualTo(KmsKeyState.UNAVAILABLE);
    }

    @Test
    @DisplayName("[M-64] describe — KeyState가 null → KmsKeyState.UNAVAILABLE (null 방어)")
    void describe_nullKeyState_returnsUnavailable() {
      // given
      DescribeKeyResponse nullStateResponse =
          DescribeKeyResponse.builder()
              .keyMetadata(KeyMetadata.builder().keyState((KeyState) null).build())
              .build();
      when(kmsClient.describeKey(any(DescribeKeyRequest.class))).thenReturn(nullStateResponse);
      KmsKeyDescribeAdapter adapter = new KmsKeyDescribeAdapter(kmsClient);

      // when
      KmsKeyState result = adapter.describe(KEY_ARN);

      // then — no NPE, fail-closed result
      assertThat(result).isEqualTo(KmsKeyState.UNAVAILABLE);
    }
  }

  // =========================================================================
  // B. 오류 전파
  // =========================================================================

  @Nested
  @DisplayName("B. 오류 전파 (error propagation)")
  class ErrorPropagation {

    @Test
    @DisplayName("[M-65] describe — KmsException → KmsKeyDescribeFailedException (원인 보존)")
    void describe_kmsExceptionThrown_wrapsInKmsKeyDescribeFailedException() {
      // given
      var kmsEx = NotFoundException.builder().message("key not found").build();
      when(kmsClient.describeKey(any(DescribeKeyRequest.class))).thenThrow(kmsEx);
      KmsKeyDescribeAdapter adapter = new KmsKeyDescribeAdapter(kmsClient);

      // when / then
      assertThatThrownBy(() -> adapter.describe(KEY_ARN))
          .isInstanceOf(KmsKeyDescribeFailedException.class)
          .hasMessageContaining("KMS DescribeKey failed")
          .satisfies(
              ex -> {
                KmsKeyDescribeFailedException descEx = (KmsKeyDescribeFailedException) ex;
                assertThat(descEx.getCause()).isSameAs(kmsEx);
                assertThat(descEx.getCode()).isEqualTo("WEB3_018");
              });
    }

    @Test
    @DisplayName("[M-65b] describe — KmsInvalidStateException → KmsKeyDescribeFailedException")
    void describe_kmsInvalidStateException_wrapsCorrectly() {
      // given
      var kmsEx = KmsInvalidStateException.builder().message("key disabled").build();
      when(kmsClient.describeKey(any(DescribeKeyRequest.class))).thenThrow(kmsEx);
      KmsKeyDescribeAdapter adapter = new KmsKeyDescribeAdapter(kmsClient);

      // when / then
      assertThatThrownBy(() -> adapter.describe(KEY_ARN))
          .isInstanceOf(KmsKeyDescribeFailedException.class)
          .satisfies(ex -> assertThat(ex.getCause()).isSameAs(kmsEx));
    }
  }

  // =========================================================================
  // C. 요청 조립 검증
  // =========================================================================

  @Nested
  @DisplayName("C. 요청 조립 검증 (request assembly)")
  class RequestAssembly {

    @Test
    @DisplayName("[M-66] describe — DescribeKeyRequest.keyId()가 입력 kmsKeyId와 일치 (ArgumentCaptor)")
    void describe_capturedRequest_keyIdMatchesInput() {
      // given
      when(kmsClient.describeKey(any(DescribeKeyRequest.class)))
          .thenReturn(stubResponse(KeyState.ENABLED));
      KmsKeyDescribeAdapter adapter = new KmsKeyDescribeAdapter(kmsClient);
      ArgumentCaptor<DescribeKeyRequest> captor = ArgumentCaptor.forClass(DescribeKeyRequest.class);

      // when
      adapter.describe(SPECIFIC_KEY_ARN);

      // then
      verify(kmsClient).describeKey(captor.capture());
      assertThat(captor.getValue().keyId()).isEqualTo(SPECIFIC_KEY_ARN);
    }
  }
}
