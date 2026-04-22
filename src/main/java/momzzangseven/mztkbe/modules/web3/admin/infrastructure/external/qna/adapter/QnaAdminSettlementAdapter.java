package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.qna.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminSettlementResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminSettlementReviewResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceQnaAdminSettlementPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetQnaAdminSettlementReviewPort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.CalculateQnaAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CalculateQnaAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAdminEnabled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnQnaAdminEnabled
public class QnaAdminSettlementAdapter
    implements GetQnaAdminSettlementReviewPort, ForceQnaAdminSettlementPort {

  private final CalculateQnaAdminSettlementReviewUseCase calculateQnaAdminSettlementReviewUseCase;
  private final ExecuteQnaAdminSettlementUseCase executeQnaAdminSettlementUseCase;

  @Override
  public GetQnaAdminSettlementReviewResult getSettlementReview(Long postId, Long answerId) {
    return new GetQnaAdminSettlementReviewResult(
        calculateQnaAdminSettlementReviewUseCase.execute(
            new CalculateQnaAdminSettlementReviewQuery(postId, answerId)));
  }

  @Override
  public ForceQnaAdminSettlementResult settle(Long operatorId, Long postId, Long answerId) {
    return new ForceQnaAdminSettlementResult(
        executeQnaAdminSettlementUseCase.execute(
            new ExecuteQnaAdminSettlementCommand(operatorId, postId, answerId)));
  }
}
