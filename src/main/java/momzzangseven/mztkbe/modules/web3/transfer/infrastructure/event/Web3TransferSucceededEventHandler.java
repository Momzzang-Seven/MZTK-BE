package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionSucceededEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferIdempotencyKeyFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Synchronizes domain state after web3 transfer success events. */
@Slf4j
@Component
@RequiredArgsConstructor
public class Web3TransferSucceededEventHandler {

  private final PostJpaRepository postJpaRepository;

  @EventListener
  @Transactional
  public void handle(Web3TransactionSucceededEvent event) {
    DomainReferenceType domainType = resolveDomainType(event);
    if (domainType != DomainReferenceType.QUESTION_REWARD) {
      return;
    }

    Long answerCommentId = parseLongOrNull(event.referenceId());
    if (answerCommentId == null) {
      log.warn(
          "SUCCEEDED sync skipped: txId={}, invalid question referenceId={}",
          event.transactionId(),
          event.referenceId());
      return;
    }

    Long postId = postJpaRepository.findPostIdByAnswerCommentId(answerCommentId).orElse(null);
    if (postId == null) {
      log.warn(
          "SUCCEEDED sync skipped: txId={}, answerCommentId={} not found",
          event.transactionId(),
          answerCommentId);
      return;
    }

    int updatedRows = postJpaRepository.markSolvedByIdIfType(postId, PostType.QUESTION);
    if (updatedRows > 0) {
      log.info(
          "SUCCEEDED sync completed: txId={}, answerCommentId={}, questionPostId={}, txHash={}",
          event.transactionId(),
          answerCommentId,
          postId,
          event.txHash());
      return;
    }

    log.info(
        "SUCCEEDED sync no-op: txId={}, answerCommentId={}, questionPostId={} (already solved or non-question)",
        event.transactionId(),
        answerCommentId,
        postId);
  }

  private DomainReferenceType resolveDomainType(Web3TransactionSucceededEvent event) {
    DomainReferenceType parsedFromIdempotency =
        TokenTransferIdempotencyKeyFactory.parseDomainType(event.idempotencyKey());
    if (parsedFromIdempotency != null) {
      return parsedFromIdempotency;
    }
    if (event.referenceType() == Web3ReferenceType.LEVEL_UP_REWARD) {
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
}
