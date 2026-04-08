package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.external.level;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.level.RewardTreasuryAddressInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkCommand;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkPort;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkResult;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.CreateLevelUpRewardTransactionIntentCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.CreateLevelUpRewardTransactionIntentResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.CreateLevelUpRewardTransactionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.domain.vo.TransactionStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.RewardIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.TransferRewardTokenProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.crypto.WalletUtils;

/** RewardMztkPort adapter backed by web3_transactions idempotent intent creation. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class LevelRewardMztkAdapter implements RewardMztkPort {

  private final CreateLevelUpRewardTransactionIntentUseCase
      createLevelUpRewardTransactionIntentUseCase;
  private final TransferRewardTokenProperties rewardTokenProperties;

  @Override
  public RewardMztkResult reward(RewardMztkCommand command) {
    validate(command);

    String idempotencyKey =
        RewardIdempotencyKeyFactory.forLevelUpReward(command.userId(), command.referenceId());

    BigInteger amountWei = toWei(command.rewardMztk());
    String treasuryAddress = resolveTreasuryAddress();

    CreateLevelUpRewardTransactionIntentResult transaction =
        createLevelUpRewardTransactionIntentUseCase.execute(
            new CreateLevelUpRewardTransactionIntentCommand(
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

  private RewardMztkResult map(CreateLevelUpRewardTransactionIntentResult transaction) {
    TransactionStatus status = transaction.status();
    if (status == TransactionStatus.SUCCEEDED) {
      return RewardMztkResult.success(transaction.txHash());
    }
    if (status == TransactionStatus.UNCONFIRMED) {
      return RewardMztkResult.unconfirmed(transaction.failureReason(), transaction.txHash());
    }

    return new RewardMztkResult(
        RewardTxStatus.valueOf(status.name()), transaction.txHash(), transaction.failureReason());
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
