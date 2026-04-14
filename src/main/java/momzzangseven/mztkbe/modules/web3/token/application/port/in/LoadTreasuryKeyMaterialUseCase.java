package momzzangseven.mztkbe.modules.web3.token.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.token.application.dto.LoadTreasuryKeyMaterialQuery;
import momzzangseven.mztkbe.modules.web3.token.application.dto.LoadTreasuryKeyMaterialResult;

public interface LoadTreasuryKeyMaterialUseCase {

  Optional<LoadTreasuryKeyMaterialResult> execute(LoadTreasuryKeyMaterialQuery query);
}
