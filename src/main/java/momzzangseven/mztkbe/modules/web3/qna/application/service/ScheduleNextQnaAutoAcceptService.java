package momzzangseven.mztkbe.modules.web3.qna.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminSettleCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ScheduleNextQnaAutoAcceptResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.PrepareQnaInternalSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ScheduleNextQnaAutoAcceptUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ClaimNextQnaAutoAcceptCandidatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAcceptContextPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAutoAcceptPolicyPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAcceptStateSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAutoAcceptCandidate;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;

@Slf4j
@RequiredArgsConstructor
public class ScheduleNextQnaAutoAcceptService implements ScheduleNextQnaAutoAcceptUseCase {

  private final ClaimNextQnaAutoAcceptCandidatePort claimNextQnaAutoAcceptCandidatePort;
  private final LoadQnaAutoAcceptPolicyPort loadQnaAutoAcceptPolicyPort;
  private final LoadQnaAcceptContextPort loadQnaAcceptContextPort;
  private final LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;
  private final QnaAcceptStateSyncPort qnaAcceptStateSyncPort;
  private final PrepareQnaInternalSettlementUseCase prepareQnaInternalSettlementUseCase;
  private final Clock appClock;

  @Override
  public ScheduleNextQnaAutoAcceptResult scheduleNext(Instant now) {
    LoadQnaAutoAcceptPolicyPort.QnaAutoAcceptPolicy policy =
        loadQnaAutoAcceptPolicyPort.loadPolicy();
    LocalDateTime cutoff =
        LocalDateTime.ofInstant(now == null ? Instant.now(appClock) : now, appClock.getZone())
            .minusSeconds(policy.delaySeconds());

    QnaAutoAcceptCandidate candidate =
        claimNextQnaAutoAcceptCandidatePort.claimNextCandidate(cutoff).orElse(null);
    if (candidate == null) {
      return ScheduleNextQnaAutoAcceptResult.exhausted();
    }

    if (hasActiveIntent(QnaExecutionResourceType.QUESTION, candidate.postId())
        || hasActiveIntent(QnaExecutionResourceType.ANSWER, candidate.answerId())) {
      log.debug(
          "skip qna auto-accept due to active intent: postId={}, answerId={}",
          candidate.postId(),
          candidate.answerId());
      return ScheduleNextQnaAutoAcceptResult.skipped();
    }

    LoadQnaAcceptContextPort.QnaAcceptContext context =
        loadQnaAcceptContextPort
            .loadForUpdate(candidate.postId(), candidate.answerId())
            .orElse(null);
    if (context == null) {
      log.warn(
          "skip qna auto-accept because local accept context is missing: postId={}, answerId={}",
          candidate.postId(),
          candidate.answerId());
      return ScheduleNextQnaAutoAcceptResult.skipped();
    }

    qnaAcceptStateSyncPort.beginPendingAccept(context.postId(), context.answerId());
    prepareQnaInternalSettlementUseCase.execute(
        new PrepareAdminSettleCommand(
            context.postId(),
            context.answerId(),
            context.requesterUserId(),
            context.answerWriterUserId(),
            context.questionContent(),
            context.answerContent()));
    return ScheduleNextQnaAutoAcceptResult.scheduled();
  }

  private boolean hasActiveIntent(QnaExecutionResourceType resourceType, Long resourceId) {
    return loadQnaExecutionIntentStatePort
        .loadLatestActiveByResourceForUpdate(resourceType, String.valueOf(resourceId))
        .isPresent();
  }
}
