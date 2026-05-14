package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.Eip7702AuthorizationPolicyResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadEip7702AuthorizationTtlPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetEip7702AuthorizationPolicyServiceTest {

  @Mock private LoadEip7702AuthorizationTtlPort loadEip7702AuthorizationTtlPort;

  private GetEip7702AuthorizationPolicyService service;

  @BeforeEach
  void setUp() {
    service = new GetEip7702AuthorizationPolicyService(loadEip7702AuthorizationTtlPort);
  }

  @Test
  void execute_mapsConfiguredMinimumRemainingTtlToApplicationResult() {
    when(loadEip7702AuthorizationTtlPort.loadMinimumRemainingSeconds()).thenReturn(45L);

    Eip7702AuthorizationPolicyResult result = service.execute();

    assertThat(result.minimumRemainingSeconds()).isEqualTo(45L);
  }
}
