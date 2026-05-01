package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.external.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignDigestUseCase;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Component;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignDigestAdapter (transaction → shared bridging)")
class SignDigestAdapterTest {

  private static final String KMS_KEY_ID = "alias/reward-treasury";
  private static final String EXPECTED_ADDRESS = "0x" + "c".repeat(40);

  @Mock private SignDigestUseCase signDigestUseCase;

  @InjectMocks private SignDigestAdapter adapter;

  private static byte[] padded(int low) {
    byte[] out = new byte[32];
    out[31] = (byte) low;
    return out;
  }

  private static byte[] digestBytes() {
    byte[] out = new byte[32];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) (i + 1);
    }
    return out;
  }

  @Test
  @DisplayName(
      "[M-170] signDigest — SignDigestCommand 의 (kmsKeyId, digest, expectedAddress) 가 그대로 전달된다")
  void signDigest_buildsCommandAndMapsResultToVrs() {
    byte[] digest = digestBytes();
    SignDigestResult canned = new SignDigestResult(padded(7), padded(11), (byte) 28);
    when(signDigestUseCase.execute(new SignDigestCommand(KMS_KEY_ID, digest, EXPECTED_ADDRESS)))
        .thenReturn(canned);

    Vrs result = adapter.signDigest(KMS_KEY_ID, digest, EXPECTED_ADDRESS);

    ArgumentCaptor<SignDigestCommand> commandCaptor =
        ArgumentCaptor.forClass(SignDigestCommand.class);
    org.mockito.Mockito.verify(signDigestUseCase).execute(commandCaptor.capture());
    SignDigestCommand captured = commandCaptor.getValue();
    assertThat(captured.kmsKeyId()).isEqualTo(KMS_KEY_ID);
    assertThat(captured.digest()).isEqualTo(digest);
    assertThat(captured.expectedAddress()).isEqualTo(EXPECTED_ADDRESS);

    assertThat(result.r()).isEqualTo(canned.r());
    assertThat(result.s()).isEqualTo(canned.s());
    assertThat(result.v()).isEqualTo(canned.v());
  }

  @Test
  @DisplayName("[M-171] @Component 빈 이름이 transactionSignDigestAdapter")
  void componentBeanName_isNamespacedToTransactionModule() {
    Component component = SignDigestAdapter.class.getAnnotation(Component.class);

    assertThat(component).isNotNull();
    assertThat(component.value()).isEqualTo("transactionSignDigestAdapter");
  }
}
