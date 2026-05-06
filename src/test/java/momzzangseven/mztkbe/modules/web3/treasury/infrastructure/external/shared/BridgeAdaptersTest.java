package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.external.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.DescribeKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignDigestUseCase;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the {@code treasury → shared} bridge adapters: covers [M-125], [M-126].
 *
 * <p>Each adapter is a one-line delegate; tests verify field-by-field propagation and the mapping
 * between shared {@code SignDigestResult} ↔ shared {@link Vrs}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Treasury 브리지 어댑터 단위 테스트")
class BridgeAdaptersTest {

  @Nested
  @DisplayName("A. DescribeKmsKeyAdapter")
  class DescribeKmsKey {

    @Mock private DescribeKmsKeyUseCase useCase;

    @Test
    @DisplayName("[M-125] describe — UseCase에 그대로 위임")
    void describe_passesThroughToUseCase() {
      when(useCase.execute("kms-id")).thenReturn(KmsKeyState.PENDING_DELETION);
      DescribeKmsKeyAdapter adapter = new DescribeKmsKeyAdapter(useCase);

      KmsKeyState result = adapter.describe("kms-id");

      assertThat(result).isSameAs(KmsKeyState.PENDING_DELETION);
      verify(useCase).execute("kms-id");
    }
  }

  @Nested
  @DisplayName("B. SignDigestAdapter")
  class SignDigest {

    @Mock private SignDigestUseCase useCase;

    @Test
    @DisplayName("[M-126] signDigest — SignDigestCommand 빌드 + Vrs로 매핑")
    void signDigest_buildsCommandAndMapsResultToVrs() {
      byte[] r = new byte[32];
      r[0] = 0x01;
      byte[] s = new byte[32];
      s[0] = 0x02;
      byte v = 28;
      when(useCase.execute(any(SignDigestCommand.class))).thenReturn(new SignDigestResult(r, s, v));
      SignDigestAdapter adapter = new SignDigestAdapter(useCase);

      ArgumentCaptor<SignDigestCommand> captor = ArgumentCaptor.forClass(SignDigestCommand.class);
      byte[] digest = new byte[32];
      digest[0] = (byte) 0xAA;

      Vrs result = adapter.signDigest("kms-id", digest, "0x" + "a".repeat(40));

      verify(useCase).execute(captor.capture());
      SignDigestCommand cmd = captor.getValue();
      assertThat(cmd.kmsKeyId()).isEqualTo("kms-id");
      assertThat(cmd.digest()).isEqualTo(digest);
      assertThat(cmd.expectedAddress()).isEqualTo("0x" + "a".repeat(40));

      assertThat(result.v()).isEqualTo(v);
      assertThat(result.r()).isEqualTo(r);
      assertThat(result.s()).isEqualTo(s);
    }
  }
}
