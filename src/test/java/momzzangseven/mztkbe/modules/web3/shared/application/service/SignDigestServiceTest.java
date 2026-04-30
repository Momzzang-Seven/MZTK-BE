package momzzangseven.mztkbe.modules.web3.shared.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.KmsSignerPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link SignDigestService} — verifies orchestration, validate-before-port ordering,
 * and exception propagation.
 *
 * <p>Covers test cases M-39 through M-42 (Commit 1-3, Group D).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SignDigestService 단위 테스트")
class SignDigestServiceTest {

  private static final String VALID_KEY_ID = "key-id";
  private static final String VALID_ADDRESS = "0x" + "a".repeat(40);
  private static final byte[] VALID_DIGEST = new byte[32];

  @Mock private KmsSignerPort kmsSignerPort;

  @InjectMocks private SignDigestService service;

  // =========================================================================
  // Section D — orchestration and error propagation
  // =========================================================================

  @Nested
  @DisplayName("D. 성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-39] execute — 정상 경로: 포트를 한 번 호출하고 결과를 래핑")
    void execute_happyPath_callsPortOnceAndWrapsResult() {
      // given
      byte[] r = new byte[32];
      r[0] = (byte) 0xAA;
      byte[] s = new byte[32];
      s[0] = (byte) 0xBB;
      Vrs vrs = new Vrs(r, s, (byte) 27);

      when(kmsSignerPort.signDigest(VALID_KEY_ID, VALID_DIGEST, VALID_ADDRESS)).thenReturn(vrs);

      SignDigestCommand cmd = new SignDigestCommand(VALID_KEY_ID, VALID_DIGEST, VALID_ADDRESS);

      // when
      SignDigestResult result = service.execute(cmd);

      // then
      assertThat(result.v()).isEqualTo((byte) 27);
      assertThat(Arrays.equals(result.r(), r)).isTrue();
      assertThat(Arrays.equals(result.s(), s)).isTrue();
      verify(kmsSignerPort, times(1)).signDigest(VALID_KEY_ID, VALID_DIGEST, VALID_ADDRESS);
    }
  }

  @Nested
  @DisplayName("D. 실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("[M-40] execute — 잘못된 커맨드는 포트 호출 전에 IAE 발생")
    void execute_invalidCommand_throwsIaeBeforeCallingPort() {
      // given — blank kmsKeyId
      SignDigestCommand badCmd = new SignDigestCommand("  ", VALID_DIGEST, VALID_ADDRESS);

      // when / then
      assertThatThrownBy(() -> service.execute(badCmd))
          .isInstanceOf(IllegalArgumentException.class);

      verify(kmsSignerPort, never()).signDigest(any(), any(), any());
    }

    @Test
    @DisplayName("[M-41] execute — KmsSignerPort가 KmsSignFailedException 던지면 그대로 전파")
    void execute_portThrowsKmsSignFailedException_propagatesUnchanged() {
      // given
      KmsSignFailedException ex = new KmsSignFailedException("KMS throttled");
      when(kmsSignerPort.signDigest(any(), any(), any())).thenThrow(ex);

      SignDigestCommand cmd = new SignDigestCommand(VALID_KEY_ID, VALID_DIGEST, VALID_ADDRESS);

      // when / then
      assertThatThrownBy(() -> service.execute(cmd))
          .isInstanceOf(KmsSignFailedException.class)
          .isSameAs(ex);

      assertThat(ex.getCode()).isEqualTo("WEB3_017");
    }

    @Test
    @DisplayName("[M-42] execute — KmsSignerPort가 SignatureRecoveryException 던지면 그대로 전파")
    void execute_portThrowsSignatureRecoveryException_propagatesUnchanged() {
      // given
      SignatureRecoveryException ex = new SignatureRecoveryException("address mismatch");
      when(kmsSignerPort.signDigest(any(), any(), any())).thenThrow(ex);

      SignDigestCommand cmd = new SignDigestCommand(VALID_KEY_ID, VALID_DIGEST, VALID_ADDRESS);

      // when / then
      assertThatThrownBy(() -> service.execute(cmd))
          .isInstanceOf(SignatureRecoveryException.class)
          .isSameAs(ex);

      assertThat(ex.getCode()).isEqualTo("WEB3_016");
    }
  }
}
