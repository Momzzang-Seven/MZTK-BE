package momzzangseven.mztkbe.modules.marketplace.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.marketplace.StoreNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetStoreQuery;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetStoreResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.GetStoreQueryHandler;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadStorePort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.TrainerStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service (QueryHandler) for retrieving a trainer's store.
 *
 * <p>Single Responsibility: Retrieve trainer store information.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StoreQueryHandler implements GetStoreQueryHandler {

  private final LoadStorePort loadStorePort;

  @Override
  public GetStoreResult execute(GetStoreQuery query) {
    log.debug("Getting store for trainerId={}", query.trainerId());

    // Load store (throws 404 if not found)
    TrainerStore store =
        loadStorePort
            .findByTrainerId(query.trainerId())
            .orElseThrow(() -> new StoreNotFoundException(query.trainerId()));

    log.debug("Store found: storeId={}", store.getId());
    return GetStoreResult.from(store);
  }
}
