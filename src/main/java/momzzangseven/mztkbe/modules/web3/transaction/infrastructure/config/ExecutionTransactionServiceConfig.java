package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.ManageExecutionTransactionUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadPendingNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.TransferTransactionPersistencePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
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
      ReserveNoncePort reserveNoncePort,
      LoadPendingNoncePort loadPendingNoncePort,
      Web3ContractPort web3ContractPort) {
    return new ManageExecutionTransactionService(
        transferTransactionPersistencePort,
        updateTransactionPort,
        recordTransactionAuditPort,
        reserveNoncePort,
        loadPendingNoncePort,
        web3ContractPort);
  }
}
