package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.HandleTransferSucceededCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.HandleTransferSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.MarkQuestionPostSolvedPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferIdempotencyKeyFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HandleTransferSucceededService implements HandleTransferSucceededUseCase {

  private static final String LEVEL_UP_REWARD = "LEVEL_UP_REWARD";

  private final MarkQuestionPostSolvedPort markQuestionPostSolvedPort;
  private final QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  @Override
  @Transactional
  public void execute(HandleTransferSucceededCommand command) {
    DomainReferenceType domainType = resolveDomainType(command.idempotencyKey(), command.referenceType());
    if (domainType != DomainReferenceType.QUESTION_REWARD) {
      return;
    }

    Long postId = parseLongOrNull(command.referenceId());
    if (postId == null) {
      log.warn(
          "SUCCEEDED sync skipped: txId={}, invalid question referenceId={}",
          command.transactionId(),
          command.referenceId());
      return;
    }

    int intentUpdated =
        questionRewardIntentPersistencePort.updateStatusIfCurrentIn(
            postId,
            QuestionRewardIntentStatus.SUCCEEDED,
            EnumSet.of(
                QuestionRewardIntentStatus.PREPARE_REQUIRED, QuestionRewardIntentStatus.SUBMITTED));

    if (!isIntentSettled(postId, intentUpdated)) {
      log.warn(
          "SUCCEEDED sync skipped post update due to unsettled intent: txId={}, questionPostId={}",
          command.transactionId(),
          postId);
      return;
    }

    int updatedRows = markQuestionPostSolvedPort.markSolved(postId);
    if (updatedRows > 0) {
      log.info(
          "SUCCEEDED sync completed: txId={}, questionPostId={}, txHash={}",
          command.transactionId(),
          postId,
          command.txHash());
      return;
    }

    log.info(
        "SUCCEEDED sync no-op: txId={}, questionPostId={} (already solved or non-question)",
        command.transactionId(),
        postId);
  }

  private DomainReferenceType resolveDomainType(
      String idempotencyKey, String referenceType) {
    DomainReferenceType parsedFromIdempotency =
        TokenTransferIdempotencyKeyFactory.parseDomainType(idempotencyKey);
    if (parsedFromIdempotency != null) {
      return parsedFromIdempotency;
    }
    if (LEVEL_UP_REWARD.equals(referenceType)) {
      return DomainReferenceType.LEVEL_UP_REWARD;
    }
    return null;
  }

  private Long parseLongOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private boolean isIntentSettled(Long postId, int intentUpdated) {
    if (intentUpdated > 0) {
      return true;
    }
    return questionRewardIntentPersistencePort
        .findByPostId(postId)
        .map(intent -> intent.getStatus() == QuestionRewardIntentStatus.SUCCEEDED)
        .orElse(false);
  }
}
