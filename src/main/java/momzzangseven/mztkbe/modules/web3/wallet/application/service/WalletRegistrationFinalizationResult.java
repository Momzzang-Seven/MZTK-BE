package momzzangseven.mztkbe.modules.web3.wallet.application.service;

record WalletRegistrationFinalizationResult(String supersededExecutionIntentId) {

  static WalletRegistrationFinalizationResult noop() {
    return new WalletRegistrationFinalizationResult(null);
  }

  static WalletRegistrationFinalizationResult finalized(String supersededExecutionIntentId) {
    return new WalletRegistrationFinalizationResult(supersededExecutionIntentId);
  }

  boolean hasSupersededExecutionIntent() {
    return supersededExecutionIntentId != null && !supersededExecutionIntentId.isBlank();
  }
}
