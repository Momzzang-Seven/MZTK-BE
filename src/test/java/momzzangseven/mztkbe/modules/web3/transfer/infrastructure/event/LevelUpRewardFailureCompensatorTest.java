package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.HandleTransferFailedOnchainCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.CheckLevelUpHistoryExistsPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferTransactionReferenceType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LevelUpRewardFailureCompensatorTest {

  @Test
  void supports_onlyLevelUpRewardDomain() {
    CheckLevelUpHistoryExistsPort port = Mockito.mock(CheckLevelUpHistoryExistsPort.class);
    LevelUpRewardFailureCompensator compensator = new LevelUpRewardFailureCompensator(port);

    assertThat(compensator.supports(DomainReferenceType.LEVEL_UP_REWARD)).isTrue();
    assertThat(compensator.supports(DomainReferenceType.QUESTION_REWARD)).isFalse();
  }

  @Test
  void compensate_skipsWhenReferenceIdIsInvalid() {
    CheckLevelUpHistoryExistsPort port = Mockito.mock(CheckLevelUpHistoryExistsPort.class);
    LevelUpRewardFailureCompensator compensator = new LevelUpRewardFailureCompensator(port);

    compensator.compensate(command("not-a-number"));

    verifyNoInteractions(port);
  }

  @Test
  void compensate_skipsWhenLevelUpHistoryIsMissing() {
    CheckLevelUpHistoryExistsPort port = Mockito.mock(CheckLevelUpHistoryExistsPort.class);
    when(port.existsById(55L)).thenReturn(false);
    LevelUpRewardFailureCompensator compensator = new LevelUpRewardFailureCompensator(port);

    compensator.compensate(command("55"));

    verify(port).existsById(55L);
  }

  @Test
  void compensate_acknowledgesWhenLevelUpHistoryExists() {
    CheckLevelUpHistoryExistsPort port = Mockito.mock(CheckLevelUpHistoryExistsPort.class);
    when(port.existsById(55L)).thenReturn(true);
    LevelUpRewardFailureCompensator compensator = new LevelUpRewardFailureCompensator(port);

    compensator.compensate(command("55"));

    verify(port).existsById(55L);
  }

  private HandleTransferFailedOnchainCommand command(String referenceId) {
    return new HandleTransferFailedOnchainCommand(
        100L,
        "reward:11:" + referenceId,
        TransferTransactionReferenceType.LEVEL_UP_REWARD,
        referenceId,
        null,
        11L,
        "0x1234567890123456789012345678901234567890123456789012345678901234",
        "RECEIPT_STATUS_0");
  }
}
