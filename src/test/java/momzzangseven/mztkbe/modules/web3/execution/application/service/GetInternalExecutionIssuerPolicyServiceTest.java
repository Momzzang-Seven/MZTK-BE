package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetInternalExecutionIssuerPolicyServiceTest {

  @Mock private LoadInternalExecutionIssuerPolicyPort loadInternalExecutionIssuerPolicyPort;

  private GetInternalExecutionIssuerPolicyService service;

  @BeforeEach
  void setUp() {
    service = new GetInternalExecutionIssuerPolicyService(loadInternalExecutionIssuerPolicyPort);
  }

  @Test
  void getPolicy_mapsEnabledAndAdminSettleSupport() {
    when(loadInternalExecutionIssuerPolicyPort.loadPolicy())
        .thenReturn(
            new LoadInternalExecutionIssuerPolicyPort.InternalExecutionIssuerPolicy(
                true, 20, List.of(ExecutionActionType.QNA_ADMIN_SETTLE)));

    var result = service.getPolicy();

    assertThat(result.enabled()).isTrue();
    assertThat(result.qnaAdminSettleEnabled()).isTrue();
  }

  @Test
  void getPolicy_reportsMissingAdminSettleSupport() {
    when(loadInternalExecutionIssuerPolicyPort.loadPolicy())
        .thenReturn(
            new LoadInternalExecutionIssuerPolicyPort.InternalExecutionIssuerPolicy(
                true, 20, List.of(ExecutionActionType.TRANSFER_SEND)));

    var result = service.getPolicy();

    assertThat(result.enabled()).isTrue();
    assertThat(result.qnaAdminSettleEnabled()).isFalse();
  }
}
