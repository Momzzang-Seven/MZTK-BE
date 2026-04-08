package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.util.Optional;

public interface LoadTransferExecutionIntentPort {

  Optional<String> findLatestExecutionIntentId(Long requesterUserId, String resourceId);
}
