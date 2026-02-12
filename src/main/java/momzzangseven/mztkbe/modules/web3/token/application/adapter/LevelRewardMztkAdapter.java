package momzzangseven.mztkbe.modules.web3.token.application.adapter;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkCommand;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkPort;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkResult;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.CreateLevelUpRewardTxIntentCommand;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.SaveTransactionPort;
import momzzangseven.mztkbe.modules.web3.token.domain.RewardIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3Transaction;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** RewardMztkPort adapter backed by web3_transactions idempotent intent creation. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class LevelRewardMztkAdapter implements RewardMztkPort {

  private final SaveTransactionPort saveTransactionPort;
  private final RewardTokenProperties rewardTokenProperties;

  @Override
  public RewardMztkResult reward(RewardMztkCommand command) {
    validate(command);

    String idempotencyKey =
        RewardIdempotencyKeyFactory.forLevelUpReward(command.userId(), command.referenceId());

    BigInteger amountWei = toWei(command.rewardMztk());
    String treasuryAddress = rewardTokenProperties.getTreasury().getTreasuryAddress();

    Web3Transaction transaction =
        saveTransactionPort.saveLevelUpRewardIntent(
            new CreateLevelUpRewardTxIntentCommand(
                command.userId(),
                command.referenceId(),
                idempotencyKey,
                treasuryAddress,
                command.toWalletAddress(),
                amountWei));

    return map(transaction);
  }

  private void validate(RewardMztkCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    if (command.userId() == null || command.userId() <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (command.referenceId() == null || command.referenceId() <= 0) {
      throw new IllegalArgumentException("referenceId must be positive");
    }
    if (command.toWalletAddress() == null || command.toWalletAddress().isBlank()) {
      throw new IllegalArgumentException("toWalletAddress is required");
    }
    if (command.rewardMztk() < 0) {
      throw new IllegalArgumentException("rewardMztk must be >= 0");
    }
  }

  private BigInteger toWei(int rewardMztk) {
    return BigInteger.valueOf(rewardMztk)
        .multiply(BigInteger.TEN.pow(Math.max(0, rewardTokenProperties.getDecimals())));
  }

  private RewardMztkResult map(Web3Transaction transaction) {
    Web3TxStatus status = transaction.getStatus();
    if (status == Web3TxStatus.SUCCEEDED) {
      return RewardMztkResult.success(transaction.getTxHash());
    }
    if (status == Web3TxStatus.UNCONFIRMED) {
      return RewardMztkResult.unconfirmed(transaction.getFailureReason(), transaction.getTxHash());
    }

    return RewardMztkResult.builder()
        .status(status)
        .txHash(transaction.getTxHash())
        .failureReason(transaction.getFailureReason())
        .build();
  }
}
