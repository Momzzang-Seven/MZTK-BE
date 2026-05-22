package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import java.util.function.Supplier;

public interface RunWalletRegistrationTransactionPort {

  <T> T execute(Supplier<T> callback);
}
