package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionIssuerPolicyView;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetInternalExecutionIssuerPolicyUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoadExecutionInternalIssuerPolicyAdapterTest {

  @Mock private GetInternalExecutionIssuerPolicyUseCase getInternalExecutionIssuerPolicyUseCase;

  private LoadExecutionInternalIssuerPolicyAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new LoadExecutionInternalIssuerPolicyAdapter(getInternalExecutionIssuerPolicyUseCase);
  }

  @Test
  void loadPolicy_mapsExecutionPolicyToQnaPolicy() {
    when(getInternalExecutionIssuerPolicyUseCase.getPolicy())
        .thenReturn(new InternalExecutionIssuerPolicyView(true, true));

    var result = adapter.loadPolicy();

    assertThat(result.enabled()).isTrue();
    assertThat(result.qnaAdminSettleEnabled()).isTrue();
  }
}
