package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.level.RewardFailedOnchainException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3ValidationMessage;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadLevelRewardTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3Transaction;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.mapper.Web3TransactionMapper;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.CreateLevelUpRewardTxIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SaveTransactionPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class Web3TransactionPersistenceAdapter
    implements SaveTransactionPort, LoadLevelRewardTransactionPort {

  private final Web3TransactionJpaRepository repository;
  private final Web3TransactionMapper mapper;

  @Override
  @Transactional
  public Web3Transaction saveLevelUpRewardIntent(CreateLevelUpRewardTxIntentCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException(Web3ValidationMessage.COMMAND_REQUIRED);
    }

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
        mapper.toEntity(
            Web3Transaction.createIntent(
                command.idempotencyKey(),
                Web3ReferenceType.LEVEL_UP_REWARD,
                command.referenceId(),
                null,
                command.userId(),
                command.fromAddress().value(),
                command.toAddress().value(),
                command.amountWei(),
                LocalDateTime.now()));

    try {
      return mapper.toDomain(repository.saveAndFlush(created));
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
    return mapper.toDomain(existing);
  }
}
