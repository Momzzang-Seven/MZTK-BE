package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.LoadWalletRegistrationRecoveryStateQuery;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationRecoveryStateResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.LoadWalletRegistrationRecoveryStateUseCase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionalLoadWalletRegistrationRecoveryStateUseCase
    implements LoadWalletRegistrationRecoveryStateUseCase {

  private final LoadWalletRegistrationRecoveryStateUseCase delegate;
  private final TransactionTemplate transactionTemplate;

  TransactionalLoadWalletRegistrationRecoveryStateUseCase(
      LoadWalletRegistrationRecoveryStateUseCase delegate,
      PlatformTransactionManager transactionManager) {
    this.delegate = delegate;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setReadOnly(true);
  }

  @Override
  public Optional<WalletRegistrationRecoveryStateResult> execute(
      LoadWalletRegistrationRecoveryStateQuery query) {
    return transactionTemplate.execute(status -> delegate.execute(query));
  }
}
