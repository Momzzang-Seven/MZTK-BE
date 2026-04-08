package momzzangseven.mztkbe.modules.marketplace.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.marketplace.StoreNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetStoreCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetStoreResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.GetStoreUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadStorePort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.TrainerStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for retrieving a trainer's store.
 *
 * <p>Single Responsibility: Retrieve trainer store information.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetStoreService implements GetStoreUseCase {

  private final LoadStorePort loadStorePort;

  @Override
  public GetStoreResult execute(GetStoreCommand command) {
    log.debug("Getting store for trainerId={}", command.trainerId());

    // Load store (throws 404 if not found)
    TrainerStore store =
        loadStorePort
            .findByTrainerId(command.trainerId())
            .orElseThrow(() -> new StoreNotFoundException(command.trainerId()));

    log.debug("Store found: storeId={}", store.getId());
    return GetStoreResult.from(store);
  }
}
