package momzzangseven.mztkbe.modules.web3.execution.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Execution domain enum 기본 스펙 테스트")
class ExecutionResourceAndActionTypeTest {

  @Test
  void executionResourceType_includesWalletRegistration() {
    assertThat(ExecutionResourceType.valueOf("WALLET_REGISTRATION"))
        .isEqualTo(ExecutionResourceType.WALLET_REGISTRATION);
  }

  @Test
  void executionActionType_includesWalletEscrowApprove() {
    assertThat(ExecutionActionType.valueOf("WALLET_ESCROW_APPROVE"))
        .isEqualTo(ExecutionActionType.WALLET_ESCROW_APPROVE);
  }
}
