package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.treasury;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ProbeTreasuryWalletCapabilityUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProbeSponsorSignerCapabilityAdapterTest {

  private static final String SPONSOR_ALIAS = "sponsor-treasury";

  @Mock private ProbeTreasuryWalletCapabilityUseCase probeTreasuryWalletCapabilityUseCase;
  @InjectMocks private ProbeSponsorSignerCapabilityAdapter adapter;

  @Test
  void probe_delegatesToTreasuryUseCase_withSponsorAlias() {
    ExecutionSignerCapabilityView expected =
        ExecutionSignerCapabilityView.ready(SPONSOR_ALIAS, "0x" + "a".repeat(40));
    when(probeTreasuryWalletCapabilityUseCase.probe(SPONSOR_ALIAS)).thenReturn(expected);

    ExecutionSignerCapabilityView actual = adapter.probe();

    assertThat(actual).isSameAs(expected);
    verify(probeTreasuryWalletCapabilityUseCase).probe(SPONSOR_ALIAS);
  }
}
