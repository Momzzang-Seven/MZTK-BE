package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadExecutionInternalIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadExecutionInternalIssuerPolicyPort.ExecutionInternalIssuerPolicy;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminSignerAddressPort;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3.QnaContractCallSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaAdminExecutionConfigurationValidator 단위 테스트")
class QnaAdminExecutionConfigurationValidatorTest {

  @Mock private LoadExecutionInternalIssuerPolicyPort loadExecutionInternalIssuerPolicyPort;
  @Mock private LoadQnaAdminSignerAddressPort loadQnaAdminSignerAddressPort;
  @Mock private QnaContractCallSupport qnaContractCallSupport;

  private QnaEscrowProperties qnaEscrowProperties;
  private QnaAdminExecutionConfigurationValidator validator;

  @BeforeEach
  void setUp() {
    qnaEscrowProperties = new QnaEscrowProperties();
    qnaEscrowProperties.setQnaContractAddress("0x1111111111111111111111111111111111111111");
    validator =
        new QnaAdminExecutionConfigurationValidator(
            loadExecutionInternalIssuerPolicyPort,
            loadQnaAdminSignerAddressPort,
            qnaContractCallSupport,
            qnaEscrowProperties);
  }

  @Test
  @DisplayName("internal issuer settle 활성화 + signer relayer 등록이면 통과")
  void validateConfiguration_allowsRelayerRegisteredSigner() {
    when(loadExecutionInternalIssuerPolicyPort.loadPolicy())
        .thenReturn(new ExecutionInternalIssuerPolicy(true, true, true));
    when(loadQnaAdminSignerAddressPort.loadSignerAddress())
        .thenReturn("0x2222222222222222222222222222222222222222");
    when(qnaContractCallSupport.isRelayerRegistered(
            "0x1111111111111111111111111111111111111111",
            "0x2222222222222222222222222222222222222222"))
        .thenReturn(true);

    assertThatCode(validator::validateConfiguration).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("refund action-type 누락이면 startup validation 이 실패한다")
  void validateConfiguration_rejectsMissingRefundActionType() {
    when(loadExecutionInternalIssuerPolicyPort.loadPolicy())
        .thenReturn(new ExecutionInternalIssuerPolicy(true, true, false));

    assertThatThrownBy(validator::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("QNA_ADMIN_REFUND");
  }

  @Test
  @DisplayName("current server signer 가 relayer 등록되지 않았으면 startup validation 이 실패한다")
  void validateConfiguration_rejectsSignerThatIsNotRelayer() {
    when(loadExecutionInternalIssuerPolicyPort.loadPolicy())
        .thenReturn(new ExecutionInternalIssuerPolicy(true, true, true));
    when(loadQnaAdminSignerAddressPort.loadSignerAddress())
        .thenReturn("0x2222222222222222222222222222222222222222");
    when(qnaContractCallSupport.isRelayerRegistered(
            "0x1111111111111111111111111111111111111111",
            "0x2222222222222222222222222222222222222222"))
        .thenReturn(false);

    assertThatThrownBy(validator::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("registered relayer");
  }
}
