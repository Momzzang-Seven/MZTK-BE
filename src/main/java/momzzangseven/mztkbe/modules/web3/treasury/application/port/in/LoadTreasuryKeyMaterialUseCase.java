package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.LoadTreasuryKeyMaterialQuery;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.LoadTreasuryKeyMaterialResult;

public interface LoadTreasuryKeyMaterialUseCase {

  Optional<LoadTreasuryKeyMaterialResult> execute(LoadTreasuryKeyMaterialQuery query);
}
