package momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.adapter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.level.RewardFailedOnchainException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadLevelRewardTransactionPort;
import momzzangseven.mztkbe.modules.web3.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.CreateLevelUpRewardTxIntentCommand;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.SaveTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3Transaction;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class Web3TransactionPersistenceAdapter
    implements SaveTransactionPort, LoadLevelRewardTransactionPort {

  private final Web3TransactionJpaRepository repository;

  @Override
  @Transactional
  public Web3Transaction saveLevelUpRewardIntent(CreateLevelUpRewardTxIntentCommand command) {
    validate(command);

    Web3TransactionEntity existingByReference =
        repository
            .findByReferenceTypeAndReferenceId(
                Web3ReferenceType.LEVEL_UP_REWARD, command.referenceId())
            .orElse(null);
    if (existingByReference != null) {
      return mapAndValidateExisting(existingByReference);
    }

    Web3TransactionEntity existingByIdempotency =
        repository.findByIdempotencyKey(command.idempotencyKey()).orElse(null);
    if (existingByIdempotency != null) {
      return mapAndValidateExisting(existingByIdempotency);
    }

    Web3TransactionEntity created =
        Web3TransactionEntity.builder()
            .idempotencyKey(command.idempotencyKey())
            .referenceType(Web3ReferenceType.LEVEL_UP_REWARD)
            .referenceId(command.referenceId())
            .toUserId(command.userId())
            .fromAddress(EvmAddress.of(command.fromAddress()).value())
            .toAddress(EvmAddress.of(command.toAddress()).value())
            .amountWei(command.amountWei())
            .status(Web3TxStatus.CREATED)
            .build();

    try {
      return map(repository.saveAndFlush(created));
    } catch (DataIntegrityViolationException e) {
      Web3TransactionEntity raced =
          repository
              .findByReferenceTypeAndReferenceId(
                  Web3ReferenceType.LEVEL_UP_REWARD, command.referenceId())
              .orElseGet(
                  () ->
                      repository
                          .findByIdempotencyKey(command.idempotencyKey())
                          .orElseThrow(
                              () ->
                                  new Web3TransactionStateInvalidException(
                                      "web3_transactions race detected, but no row found", e)));
      return mapAndValidateExisting(raced);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, RewardTxView> loadByLevelUpHistoryIds(Collection<Long> levelUpHistoryIds) {
    if (levelUpHistoryIds == null || levelUpHistoryIds.isEmpty()) {
      return Map.of();
    }

    var referenceIds = levelUpHistoryIds.stream().map(String::valueOf).toList();

    Map<Long, RewardTxView> views = new LinkedHashMap<>();
    repository
        .findByReferenceTypeAndReferenceIdIn(Web3ReferenceType.LEVEL_UP_REWARD, referenceIds)
        .forEach(
            entity -> {
              Long levelUpHistoryId = Long.parseLong(entity.getReferenceId());
              views.put(levelUpHistoryId, new RewardTxView(entity.getStatus(), entity.getTxHash()));
            });

    return views;
  }

  private Web3Transaction mapAndValidateExisting(Web3TransactionEntity existing) {
    if (existing.getStatus() == Web3TxStatus.FAILED_ONCHAIN) {
      throw new RewardFailedOnchainException(
          existing.getReferenceType().name(), existing.getReferenceId());
    }
    return map(existing);
  }

  private void validate(CreateLevelUpRewardTxIntentCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    if (command.userId() == null || command.userId() <= 0) {
      throw new Web3InvalidInputException("userId must be positive");
    }
    if (command.levelUpHistoryId() == null || command.levelUpHistoryId() <= 0) {
      throw new Web3InvalidInputException("levelUpHistoryId must be positive");
    }
    if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
      throw new Web3InvalidInputException("idempotencyKey is required");
    }
    if (command.fromAddress() == null || command.fromAddress().isBlank()) {
      throw new Web3InvalidInputException("fromAddress is required");
    }
    if (command.toAddress() == null || command.toAddress().isBlank()) {
      throw new Web3InvalidInputException("toAddress is required");
    }
    EvmAddress.of(command.fromAddress());
    EvmAddress.of(command.toAddress());
    if (command.amountWei() == null || command.amountWei().signum() < 0) {
      throw new Web3InvalidInputException("amountWei must be >= 0");
    }
  }

  private Web3Transaction map(Web3TransactionEntity entity) {
    return Web3Transaction.builder()
        .id(entity.getId())
        .idempotencyKey(entity.getIdempotencyKey())
        .referenceType(entity.getReferenceType())
        .referenceId(entity.getReferenceId())
        .fromUserId(entity.getFromUserId())
        .toUserId(entity.getToUserId())
        .fromAddress(entity.getFromAddress())
        .toAddress(entity.getToAddress())
        .amountWei(entity.getAmountWei())
        .nonce(entity.getNonce())
        .status(entity.getStatus())
        .txHash(entity.getTxHash())
        .signedAt(entity.getSignedAt())
        .broadcastedAt(entity.getBroadcastedAt())
        .confirmedAt(entity.getConfirmedAt())
        .signedRawTx(entity.getSignedRawTx())
        .failureReason(entity.getFailureReason())
        .processingUntil(entity.getProcessingUntil())
        .processingBy(entity.getProcessingBy())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
