package momzzangseven.mztkbe.modules.web3.shared.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.ProbeExecutionSignerCapabilityPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression guard for the alias literal passed by the QnaAdmin signer-capability probe.
 *
 * <p>The service exists solely to delegate to {@code probe(walletAlias)} with the sponsor alias. If
 * a future refactor accidentally hard-codes "reward-treasury" or empties the alias, the
 * sponsor-side health probe stops reflecting the real signer state — only end-to-end tests would
 * catch it. This unit test pins the contract.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProbeExecutionSignerCapabilityService 단위 테스트")
class ProbeExecutionSignerCapabilityServiceTest {

  @Mock private ProbeExecutionSignerCapabilityPort probeExecutionSignerCapabilityPort;

  @InjectMocks private ProbeExecutionSignerCapabilityService service;

  @Test
  @DisplayName("execute — 포트 probe 를 정확히 'sponsor-treasury' alias 로 한 번 호출")
  void execute_callsProbePortWithSponsorAlias() {
    // given
    ExecutionSignerCapabilityView view =
        ExecutionSignerCapabilityView.ready(
            "sponsor-treasury", "0x0000000000000000000000000000000000000001");
    when(probeExecutionSignerCapabilityPort.probe("sponsor-treasury")).thenReturn(view);

    // when
    ExecutionSignerCapabilityView result = service.execute();

    // then
    assertThat(result).isSameAs(view);
    verify(probeExecutionSignerCapabilityPort).probe("sponsor-treasury");
    verifyNoMoreInteractions(probeExecutionSignerCapabilityPort);
  }

  @Test
  @DisplayName("execute — 포트가 반환한 view 를 변형 없이 그대로 전달")
  void execute_passesThroughPortResultUnchanged() {
    // given — slot-missing path: covers QnaAdmin Health DOWN scenario
    ExecutionSignerCapabilityView view =
        ExecutionSignerCapabilityView.slotMissing("sponsor-treasury");
    when(probeExecutionSignerCapabilityPort.probe("sponsor-treasury")).thenReturn(view);

    // when
    ExecutionSignerCapabilityView result = service.execute();

    // then
    assertThat(result).isSameAs(view);
    assertThat(result.walletAlias()).isEqualTo("sponsor-treasury");
  }
}
