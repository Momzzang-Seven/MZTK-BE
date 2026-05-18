package momzzangseven.mztkbe.modules.web3.execution.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Execution enum code 기본 스펙 테스트")
class ExecutionResourceAndActionTypeCodeTest {

  @Test
  void executionResourceTypeCode_includesWalletRegistration() {
    assertThat(ExecutionResourceTypeCode.valueOf("WALLET_REGISTRATION"))
        .isEqualTo(ExecutionResourceTypeCode.WALLET_REGISTRATION);
  }

  @Test
  void executionActionTypeCode_includesWalletEscrowApprove() {
    assertThat(ExecutionActionTypeCode.valueOf("WALLET_ESCROW_APPROVE"))
        .isEqualTo(ExecutionActionTypeCode.WALLET_ESCROW_APPROVE);
  }
}
