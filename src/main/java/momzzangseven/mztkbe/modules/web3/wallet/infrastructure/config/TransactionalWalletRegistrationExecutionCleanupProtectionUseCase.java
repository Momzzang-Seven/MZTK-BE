package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationExecutionCleanupCandidate;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.FilterWalletRegistrationExecutionCleanupCandidatesUseCase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionalWalletRegistrationExecutionCleanupProtectionUseCase
    implements FilterWalletRegistrationExecutionCleanupCandidatesUseCase {

  private final FilterWalletRegistrationExecutionCleanupCandidatesUseCase delegate;
  private final TransactionTemplate transactionTemplate;

  TransactionalWalletRegistrationExecutionCleanupProtectionUseCase(
      FilterWalletRegistrationExecutionCleanupCandidatesUseCase delegate,
      PlatformTransactionManager transactionManager) {
    this.delegate = delegate;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setReadOnly(true);
  }

  @Override
  public List<Long> filterDeletableFinalizedIntentIds(
      List<WalletRegistrationExecutionCleanupCandidate> candidates) {
    return transactionTemplate.execute(
        status -> delegate.filterDeletableFinalizedIntentIds(candidates));
  }
}
