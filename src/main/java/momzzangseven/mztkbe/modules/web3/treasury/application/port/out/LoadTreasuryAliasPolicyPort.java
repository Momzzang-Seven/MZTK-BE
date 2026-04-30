package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

import java.util.Set;

public interface LoadTreasuryAliasPolicyPort {

  String defaultRewardTreasuryAlias();

  Set<String> allowedAliases();
}
