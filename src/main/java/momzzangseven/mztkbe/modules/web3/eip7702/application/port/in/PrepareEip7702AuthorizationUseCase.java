package momzzangseven.mztkbe.modules.web3.eip7702.application.port.in;

import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareEip7702AuthorizationCommand;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareEip7702AuthorizationResult;

public interface PrepareEip7702AuthorizationUseCase {

  PrepareEip7702AuthorizationResult execute(PrepareEip7702AuthorizationCommand command);
}
