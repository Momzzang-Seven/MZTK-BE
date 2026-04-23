package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.external.level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.global.error.level.RewardTreasuryAddressInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkCommand;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkResult;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryAddressProjectionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.CreateLevelUpRewardTransactionIntentCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.CreateLevelUpRewardTransactionIntentResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.CreateLevelUpRewardTransactionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.domain.vo.TransactionStatus;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadRewardTreasurySignerConfigPort;
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

  @Mock
  private CreateLevelUpRewardTransactionIntentUseCase createLevelUpRewardTransactionIntentUseCase;

  @Mock private LoadRewardTreasurySignerConfigPort loadRewardTreasurySignerConfigPort;
  @Mock private LoadTreasuryAddressProjectionPort loadTreasuryAddressProjectionPort;

  private TransferRewardTokenProperties properties;
  private LevelRewardMztkAdapter adapter;

  @BeforeEach
  void setUp() {
    properties = new TransferRewardTokenProperties();
    properties.setDecimals(18);
    lenient()
        .when(loadRewardTreasurySignerConfigPort.load())
        .thenReturn(
            new LoadRewardTreasurySignerConfigPort.RewardTreasurySignerConfig(
                "reward-treasury", "test-kek"));
    lenient()
        .when(loadTreasuryAddressProjectionPort.loadAddressByAlias("reward-treasury"))
        .thenReturn(Optional.of(TREASURY));
    adapter =
        new LevelRewardMztkAdapter(
            createLevelUpRewardTransactionIntentUseCase,
            properties,
            loadRewardTreasurySignerConfigPort,
            loadTreasuryAddressProjectionPort);
  }

  @Test
  void reward_mapsSucceededTransactionToSuccessResult() {
    when(createLevelUpRewardTransactionIntentUseCase.execute(any()))
        .thenReturn(
            new CreateLevelUpRewardTransactionIntentResult(
                TransactionStatus.SUCCEEDED, "0x" + "c".repeat(64), null));

    RewardMztkResult result = adapter.reward(validCommand(3));

    assertThat(result.status()).isEqualTo(RewardTxStatus.SUCCEEDED);
    assertThat(result.txHash()).isEqualTo("0x" + "c".repeat(64));
  }

  @Test
  void reward_mapsUnconfirmedTransactionToUnconfirmedResult() {
    when(createLevelUpRewardTransactionIntentUseCase.execute(any()))
        .thenReturn(
            new CreateLevelUpRewardTransactionIntentResult(
                TransactionStatus.UNCONFIRMED, "0x" + "c".repeat(64), "TIMEOUT"));

    RewardMztkResult result = adapter.reward(validCommand(2));

    assertThat(result.status()).isEqualTo(RewardTxStatus.UNCONFIRMED);
    assertThat(result.failureReason()).isEqualTo("TIMEOUT");
  }

  @Test
  void reward_mapsOtherStatusUsingNameMapping() {
    when(createLevelUpRewardTransactionIntentUseCase.execute(any()))
        .thenReturn(
            new CreateLevelUpRewardTransactionIntentResult(
                TransactionStatus.PENDING, "0x" + "c".repeat(64), null));

    RewardMztkResult result = adapter.reward(validCommand(1));

    assertThat(result.status()).isEqualTo(RewardTxStatus.PENDING);
  }

  @Test
  void reward_convertsRewardToWei_usingConfiguredDecimals() {
    properties.setDecimals(2);
    when(createLevelUpRewardTransactionIntentUseCase.execute(any()))
        .thenReturn(
            new CreateLevelUpRewardTransactionIntentResult(
                TransactionStatus.CREATED, null, "QUEUED"));

    adapter.reward(validCommand(7));

    ArgumentCaptor<CreateLevelUpRewardTransactionIntentCommand> captor =
        ArgumentCaptor.forClass(CreateLevelUpRewardTransactionIntentCommand.class);
    verify(createLevelUpRewardTransactionIntentUseCase).execute(captor.capture());
    assertThat(captor.getValue().amountWei()).isEqualTo(BigInteger.valueOf(700));
  }

  @Test
  void reward_usesZeroScale_whenDecimalsConfiguredNegative() {
    properties.setDecimals(-3);
    when(createLevelUpRewardTransactionIntentUseCase.execute(any()))
        .thenReturn(
            new CreateLevelUpRewardTransactionIntentResult(
                TransactionStatus.CREATED, null, "QUEUED"));

    adapter.reward(validCommand(7));

    ArgumentCaptor<CreateLevelUpRewardTransactionIntentCommand> captor =
        ArgumentCaptor.forClass(CreateLevelUpRewardTransactionIntentCommand.class);
    verify(createLevelUpRewardTransactionIntentUseCase).execute(captor.capture());
    assertThat(captor.getValue().amountWei()).isEqualTo(BigInteger.valueOf(7));
  }

  @Test
  void reward_rejectsNullCommand() {
    assertThatThrownBy(() -> adapter.reward(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void reward_rejectsNonPositiveUserId() {
    assertThatThrownBy(() -> new RewardMztkCommand(0L, 1, 77L, EvmAddress.of(TO_WALLET)))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void reward_rejectsNonPositiveReferenceId() {
    assertThatThrownBy(() -> new RewardMztkCommand(1L, 1, 0L, EvmAddress.of(TO_WALLET)))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void reward_rejectsNullWalletAddress() {
    assertThatThrownBy(() -> new RewardMztkCommand(1L, 1, 77L, null))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void reward_rejectsNegativeRewardAmount() {
    assertThatThrownBy(() -> new RewardMztkCommand(1L, -1, 77L, EvmAddress.of(TO_WALLET)))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void reward_rejectsNonPositiveUserId_whenCommandBypassesRecordValidation() {
    RewardMztkCommand command = mock(RewardMztkCommand.class);
    when(command.userId()).thenReturn(0L);

    assertThatThrownBy(() -> adapter.reward(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("userId must be positive");
  }

  @Test
  void reward_rejectsNullUserId_whenCommandBypassesRecordValidation() {
    RewardMztkCommand command = mock(RewardMztkCommand.class);
    when(command.userId()).thenReturn(null);

    assertThatThrownBy(() -> adapter.reward(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("userId must be positive");
  }

  @Test
  void reward_rejectsNonPositiveReferenceId_whenCommandBypassesRecordValidation() {
    RewardMztkCommand command = mock(RewardMztkCommand.class);
    when(command.userId()).thenReturn(1L);
    when(command.referenceId()).thenReturn(0L);

    assertThatThrownBy(() -> adapter.reward(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId must be positive");
  }

  @Test
  void reward_rejectsNullReferenceId_whenCommandBypassesRecordValidation() {
    RewardMztkCommand command = mock(RewardMztkCommand.class);
    when(command.userId()).thenReturn(1L);
    when(command.referenceId()).thenReturn(null);

    assertThatThrownBy(() -> adapter.reward(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId must be positive");
  }

  @Test
  void reward_rejectsNullWalletAddress_whenCommandBypassesRecordValidation() {
    RewardMztkCommand command = mock(RewardMztkCommand.class);
    when(command.userId()).thenReturn(1L);
    when(command.referenceId()).thenReturn(77L);
    when(command.toWalletAddress()).thenReturn(null);

    assertThatThrownBy(() -> adapter.reward(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("toWalletAddress is required");
  }

  @Test
  void reward_rejectsNegativeReward_whenCommandBypassesRecordValidation() {
    RewardMztkCommand command = mock(RewardMztkCommand.class);
    when(command.userId()).thenReturn(1L);
    when(command.referenceId()).thenReturn(77L);
    when(command.toWalletAddress()).thenReturn(EvmAddress.of(TO_WALLET));
    when(command.rewardMztk()).thenReturn(-1);

    assertThatThrownBy(() -> adapter.reward(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("rewardMztk must be >= 0");
  }

  @Test
  void reward_rejectsMissingRewardTreasuryAddressProjection() {
    when(loadTreasuryAddressProjectionPort.loadAddressByAlias("reward-treasury"))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> adapter.reward(validCommand(3)))
        .isInstanceOf(RewardTreasuryAddressInvalidException.class);
  }

  private RewardMztkCommand validCommand(int reward) {
    return new RewardMztkCommand(1L, reward, 77L, EvmAddress.of(TO_WALLET));
  }
}
