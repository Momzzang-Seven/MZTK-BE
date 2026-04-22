package momzzangseven.mztkbe.modules.web3.admin.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetQnaAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetQnaAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceQnaAdminRefundPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceQnaAdminSettlementPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetQnaAdminRefundReviewPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetQnaAdminSettlementReviewPort;
import momzzangseven.mztkbe.modules.web3.admin.application.service.ForceQnaAdminRefundService;
import momzzangseven.mztkbe.modules.web3.admin.application.service.ForceQnaAdminSettlementService;
import momzzangseven.mztkbe.modules.web3.admin.application.service.GetQnaAdminRefundReviewService;
import momzzangseven.mztkbe.modules.web3.admin.application.service.GetQnaAdminSettlementReviewService;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAdminEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnQnaAdminEnabled
public class QnaAdminServiceConfig {

  @Bean
  GetQnaAdminSettlementReviewUseCase getQnaAdminSettlementReviewUseCase(
      GetQnaAdminSettlementReviewPort getQnaAdminSettlementReviewPort) {
    return new GetQnaAdminSettlementReviewService(getQnaAdminSettlementReviewPort);
  }

  @Bean
  ForceQnaAdminSettlementUseCase forceQnaAdminSettlementUseCase(
      ForceQnaAdminSettlementPort forceQnaAdminSettlementPort) {
    return new ForceQnaAdminSettlementService(forceQnaAdminSettlementPort);
  }

  @Bean
  GetQnaAdminRefundReviewUseCase getQnaAdminRefundReviewUseCase(
      GetQnaAdminRefundReviewPort getQnaAdminRefundReviewPort) {
    return new GetQnaAdminRefundReviewService(getQnaAdminRefundReviewPort);
  }

  @Bean
  ForceQnaAdminRefundUseCase forceQnaAdminRefundUseCase(
      ForceQnaAdminRefundPort forceQnaAdminRefundPort) {
    return new ForceQnaAdminRefundService(forceQnaAdminRefundPort);
  }
}
