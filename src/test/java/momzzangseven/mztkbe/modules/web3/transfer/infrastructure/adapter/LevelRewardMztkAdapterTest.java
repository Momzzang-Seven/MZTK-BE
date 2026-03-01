package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.level.RewardTreasuryAddressInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkCommand;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkResult;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.CreateLevelUpRewardTxIntentCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.SaveTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3Transaction;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.TransferRewardTokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LevelRewardMztkAdapterTest {

  private static final String TREASURY = "0x" + "a".repeat(40);
  private static final String TO_WALLET = "0x" + "b".repeat(40);

  @Mock private SaveTransactionPort saveTransactionPort;

  private TransferRewardTokenProperties properties;
  private LevelRewardMztkAdapter adapter;

  @BeforeEach
  void setUp() {
    properties = new TransferRewardTokenProperties();
    properties.setDecimals(18);
    properties.getTreasury().setTreasuryAddress(TREASURY);
    adapter = new LevelRewardMztkAdapter(saveTransactionPort, properties);
  }

  @Test
  void reward_mapsSucceededTransactionToSuccessResult() {
    when(saveTransactionPort.saveLevelUpRewardIntent(any(CreateLevelUpRewardTxIntentCommand.class)))
        .thenReturn(web3Tx(Web3TxStatus.SUCCEEDED, "0x" + "c".repeat(64), null));

    RewardMztkResult result = adapter.reward(validCommand(3));

    assertThat(result.status()).isEqualTo(RewardTxStatus.SUCCEEDED);
    assertThat(result.txHash()).isEqualTo("0x" + "c".repeat(64));
  }

  @Test
  void reward_mapsUnconfirmedTransactionToUnconfirmedResult() {
    when(saveTransactionPort.saveLevelUpRewardIntent(any(CreateLevelUpRewardTxIntentCommand.class)))
        .thenReturn(web3Tx(Web3TxStatus.UNCONFIRMED, "0x" + "c".repeat(64), "TIMEOUT"));

    RewardMztkResult result = adapter.reward(validCommand(2));

    assertThat(result.status()).isEqualTo(RewardTxStatus.UNCONFIRMED);
    assertThat(result.failureReason()).isEqualTo("TIMEOUT");
  }

  @Test
  void reward_mapsOtherStatusUsingNameMapping() {
    when(saveTransactionPort.saveLevelUpRewardIntent(any(CreateLevelUpRewardTxIntentCommand.class)))
        .thenReturn(web3Tx(Web3TxStatus.PENDING, "0x" + "c".repeat(64), null));

    RewardMztkResult result = adapter.reward(validCommand(1));

    assertThat(result.status()).isEqualTo(RewardTxStatus.PENDING);
  }

  @Test
  void reward_convertsRewardToWei_usingConfiguredDecimals() {
    properties.setDecimals(2);
    when(saveTransactionPort.saveLevelUpRewardIntent(any(CreateLevelUpRewardTxIntentCommand.class)))
        .thenReturn(web3Tx(Web3TxStatus.CREATED, null, "QUEUED"));

    adapter.reward(validCommand(7));

    ArgumentCaptor<CreateLevelUpRewardTxIntentCommand> captor =
        ArgumentCaptor.forClass(CreateLevelUpRewardTxIntentCommand.class);
    verify(saveTransactionPort).saveLevelUpRewardIntent(captor.capture());
    assertThat(captor.getValue().amountWei()).isEqualTo(BigInteger.valueOf(700));
  }

  @Test
  void reward_usesZeroScale_whenDecimalsConfiguredNegative() {
    properties.setDecimals(-3);
    when(saveTransactionPort.saveLevelUpRewardIntent(any(CreateLevelUpRewardTxIntentCommand.class)))
        .thenReturn(web3Tx(Web3TxStatus.CREATED, null, "QUEUED"));

    adapter.reward(validCommand(7));

    ArgumentCaptor<CreateLevelUpRewardTxIntentCommand> captor =
        ArgumentCaptor.forClass(CreateLevelUpRewardTxIntentCommand.class);
    verify(saveTransactionPort).saveLevelUpRewardIntent(captor.capture());
    assertThat(captor.getValue().amountWei()).isEqualTo(BigInteger.valueOf(7));
  }

  @Test
  void reward_rejectsNullCommand() {
    assertThatThrownBy(() -> adapter.reward(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void reward_rejectsBlankTreasuryAddress() {
    properties.getTreasury().setTreasuryAddress(" ");
    assertThatThrownBy(() -> adapter.reward(validCommand(3)))
        .isInstanceOf(RewardTreasuryAddressInvalidException.class);
  }

  @Test
  void reward_rejectsInvalidTreasuryAddressFormat() {
    properties.getTreasury().setTreasuryAddress("not-address");
    assertThatThrownBy(() -> adapter.reward(validCommand(3)))
        .isInstanceOf(Web3InvalidInputException.class);
  }

  private RewardMztkCommand validCommand(int reward) {
    return new RewardMztkCommand(1L, reward, 77L, EvmAddress.of(TO_WALLET));
  }

  private Web3Transaction web3Tx(Web3TxStatus status, String txHash, String failureReason) {
    LocalDateTime now = LocalDateTime.now();
    return Web3Transaction.reconstitute(
        11L,
        "idem-1",
        Web3ReferenceType.LEVEL_UP_REWARD,
        "77",
        1L,
        2L,
        TREASURY,
        TO_WALLET,
        BigInteger.ONE,
        1L,
        status,
        txHash,
        now,
        now,
        now,
        "0xdead",
        failureReason,
        null,
        null,
        now,
        now);
  }
}

