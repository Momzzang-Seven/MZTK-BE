package momzzangseven.mztkbe.modules.post.infrastructure.external.web3;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadQuestionPublicationEvidencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionPublicationEvidence;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaQuestionPublicationEvidenceQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaQuestionPublicationEvidenceResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaQuestionPublicationEvidenceUseCase;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnUserExecutionEnabled
public class QuestionPublicationEvidenceAdapter implements LoadQuestionPublicationEvidencePort {

  private final GetQnaQuestionPublicationEvidenceUseCase getQnaQuestionPublicationEvidenceUseCase;

  @Override
  public QuestionPublicationEvidence loadEvidence(Long postId, Long requesterUserId) {
    QnaQuestionPublicationEvidenceResult result =
        getQnaQuestionPublicationEvidenceUseCase.execute(
            new GetQnaQuestionPublicationEvidenceQuery(postId, requesterUserId));
    return new QuestionPublicationEvidence(
        true,
        result.projectionExists(),
        result.projectionState(),
        result.activeCreateIntentExists(),
        result.terminalCreateIntentExists(),
        result.latestCreateIntentStatus(),
        result.latestCreateExecutionIntentId());
  }
}
