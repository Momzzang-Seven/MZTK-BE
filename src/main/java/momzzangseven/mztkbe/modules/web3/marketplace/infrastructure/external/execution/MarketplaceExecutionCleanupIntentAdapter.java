package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.execution;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionIntentCleanupView;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentCleanupViewUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionCleanupIntent;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceExecutionCleanupIntentPort;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "eip7702.cleanup.enabled"},
    havingValue = "true")
public class MarketplaceExecutionCleanupIntentAdapter
    implements LoadMarketplaceExecutionCleanupIntentPort {

  private static final Set<String> MARKETPLACE_USER_ACTIONS =
      Set.of(
          "MARKETPLACE_CLASS_PURCHASE",
          "MARKETPLACE_CLASS_CANCEL",
          "MARKETPLACE_CLASS_CONFIRM",
          "MARKETPLACE_CLASS_EXPIRED_REFUND");

  private final GetExecutionIntentCleanupViewUseCase getExecutionIntentCleanupViewUseCase;

  @Override
  public List<MarketplaceExecutionCleanupIntent> loadByIds(List<Long> intentIds) {
    return getExecutionIntentCleanupViewUseCase.getCleanupViewsByIds(intentIds).stream()
        .filter(this::isMarketplaceUserOrder)
        .map(
            view ->
                new MarketplaceExecutionCleanupIntent(
                    view.id(),
                    view.publicId(),
                    view.resourceId(),
                    MarketplaceExecutionActionType.valueOf(view.actionType().name()),
                    view.requesterUserId(),
                    view.payloadSnapshotJson()))
        .toList();
  }

  private boolean isMarketplaceUserOrder(ExecutionIntentCleanupView view) {
    return "ORDER".equals(view.resourceType().name())
        && MARKETPLACE_USER_ACTIONS.contains(view.actionType().name());
  }
}
