package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3ConfigInvalidException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.PrecheckMarketplacePurchaseUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.PrepareMarketplaceUserExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceEscrowCallDataPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceUserExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceActiveWalletPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplacePurchaseConfigPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.PrecheckMarketplacePurchaseFundingPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SignMarketplaceServerSigPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SubmitMarketplaceExecutionDraftPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Fail-fast guard for production marketplace user EIP-7702 wiring. */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
    "'${web3.eip7702.enabled:false}' == 'true' "
        + "&& '${web3.marketplace.user-execution.fail-fast:false}' == 'true'")
public class MarketplaceRuntimeProfileValidator {

  private final ObjectProvider<PrecheckMarketplacePurchaseUseCase> precheckPurchaseUseCase;
  private final ObjectProvider<PrepareMarketplaceUserExecutionUseCase> prepareExecutionUseCase;
  private final ObjectProvider<LoadMarketplaceActiveWalletPort> loadActiveWalletPort;
  private final ObjectProvider<LoadMarketplacePurchaseConfigPort> loadPurchaseConfigPort;
  private final ObjectProvider<PrecheckMarketplacePurchaseFundingPort> precheckFundingPort;
  private final ObjectProvider<BuildMarketplaceEscrowCallDataPort> buildCallDataPort;
  private final ObjectProvider<SignMarketplaceServerSigPort> signServerSigPort;
  private final ObjectProvider<BuildMarketplaceUserExecutionDraftPort> buildDraftPort;
  private final ObjectProvider<SubmitMarketplaceExecutionDraftPort> submitDraftPort;

  @PostConstruct
  void validateOnStartup() {
    List<String> missing = new ArrayList<>();
    require("PrecheckMarketplacePurchaseUseCase", precheckPurchaseUseCase, missing);
    require("PrepareMarketplaceUserExecutionUseCase", prepareExecutionUseCase, missing);
    require("LoadMarketplaceActiveWalletPort", loadActiveWalletPort, missing);
    require("LoadMarketplacePurchaseConfigPort", loadPurchaseConfigPort, missing);
    require("PrecheckMarketplacePurchaseFundingPort", precheckFundingPort, missing);
    require("BuildMarketplaceEscrowCallDataPort", buildCallDataPort, missing);
    require("SignMarketplaceServerSigPort", signServerSigPort, missing);
    require("BuildMarketplaceUserExecutionDraftPort", buildDraftPort, missing);
    require("SubmitMarketplaceExecutionDraftPort", submitDraftPort, missing);

    if (!missing.isEmpty()) {
      throw new Web3ConfigInvalidException(
          "Marketplace user execution runtime misconfigured: missing beans "
              + String.join(", ", missing));
    }
  }

  private static void require(
      String beanName, ObjectProvider<?> provider, List<String> missingBeanNames) {
    if (provider.getIfAvailable() == null) {
      missingBeanNames.add(beanName);
    }
  }
}
