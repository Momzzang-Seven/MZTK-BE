package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

public interface LoadWalletRegistrationPolicyPort {

  int sessionTtlSeconds();

  int finalizationRetryBackoffSeconds();
}
