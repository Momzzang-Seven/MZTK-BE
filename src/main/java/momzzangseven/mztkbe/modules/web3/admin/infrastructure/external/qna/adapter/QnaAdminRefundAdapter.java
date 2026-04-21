package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.qna.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminRefundResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminRefundReviewResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceQnaAdminRefundPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetQnaAdminRefundReviewPort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.CalculateQnaAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CalculateQnaAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAdminEnabled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnQnaAdminEnabled
public class QnaAdminRefundAdapter implements GetQnaAdminRefundReviewPort, ForceQnaAdminRefundPort {

  private final CalculateQnaAdminRefundReviewUseCase calculateQnaAdminRefundReviewUseCase;
  private final ExecuteQnaAdminRefundUseCase executeQnaAdminRefundUseCase;

  @Override
  public GetQnaAdminRefundReviewResult getRefundReview(Long postId) {
    return new GetQnaAdminRefundReviewResult(
        calculateQnaAdminRefundReviewUseCase.execute(
            new CalculateQnaAdminRefundReviewQuery(postId)));
  }

  @Override
  public ForceQnaAdminRefundResult refund(Long operatorId, Long postId) {
    return new ForceQnaAdminRefundResult(
        executeQnaAdminRefundUseCase.execute(new ExecuteQnaAdminRefundCommand(operatorId, postId)));
  }
}
