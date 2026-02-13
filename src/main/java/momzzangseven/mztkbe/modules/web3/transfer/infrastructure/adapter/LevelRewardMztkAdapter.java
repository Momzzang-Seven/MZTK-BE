package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.level.RewardTreasuryAddressInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkCommand;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkPort;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkResult;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.CreateLevelUpRewardTxIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SaveTransactionPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.support.RewardIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3Transaction;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.crypto.WalletUtils;

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
    String treasuryAddress = resolveTreasuryAddress();

    Web3Transaction transaction =
        saveTransactionPort.saveLevelUpRewardIntent(
            new CreateLevelUpRewardTxIntentCommand(
                command.userId(),
                command.referenceId(),
                idempotencyKey,
                EvmAddress.of(treasuryAddress),
                command.toWalletAddress(),
                amountWei));

    return map(transaction);
  }

  private void validate(RewardMztkCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    if (command.userId() == null || command.userId() <= 0) {
      throw new Web3InvalidInputException("userId must be positive");
    }
    if (command.referenceId() == null || command.referenceId() <= 0) {
      throw new Web3InvalidInputException("referenceId must be positive");
    }
    if (command.toWalletAddress() == null) {
      throw new Web3InvalidInputException("toWalletAddress is required");
    }
    if (command.rewardMztk() < 0) {
      throw new Web3InvalidInputException("rewardMztk must be >= 0");
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

    return new RewardMztkResult(status, transaction.getTxHash(), transaction.getFailureReason());
  }

  private String resolveTreasuryAddress() {
    String treasuryAddress = rewardTokenProperties.getTreasury().getTreasuryAddress();
    if (treasuryAddress == null || treasuryAddress.isBlank()) {
      throw new RewardTreasuryAddressInvalidException(treasuryAddress);
    }

    String normalized = EvmAddress.of(treasuryAddress).value();
    if (!WalletUtils.isValidAddress(normalized)) {
      throw new RewardTreasuryAddressInvalidException(treasuryAddress);
    }
    return normalized;
  }
}
