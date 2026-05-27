package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.ManageExecutionTransactionUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce.CoordinateSponsorNonceUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.TransferTransactionPersistencePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.LoadSponsorChainNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.service.ManageExecutionTransactionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnAnyExecutionEnabled
public class ExecutionTransactionServiceConfig {

  @Bean
  ManageExecutionTransactionUseCase manageExecutionTransactionUseCase(
      TransferTransactionPersistencePort transferTransactionPersistencePort,
      UpdateTransactionPort updateTransactionPort,
      RecordTransactionAuditPort recordTransactionAuditPort,
      Web3ContractPort web3ContractPort,
      LoadSponsorChainNoncePort loadSponsorChainNoncePort,
      CoordinateSponsorNonceUseCase coordinateSponsorNonceUseCase) {
    return new ManageExecutionTransactionService(
        transferTransactionPersistencePort,
        updateTransactionPort,
        recordTransactionAuditPort,
        web3ContractPort,
        loadSponsorChainNoncePort,
        coordinateSponsorNonceUseCase);
  }
}
