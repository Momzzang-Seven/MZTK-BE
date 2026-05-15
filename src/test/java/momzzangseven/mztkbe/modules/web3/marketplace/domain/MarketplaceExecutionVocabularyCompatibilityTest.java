package momzzangseven.mztkbe.modules.web3.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionActionTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceStatusCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceType;
import org.junit.jupiter.api.Test;

class MarketplaceExecutionVocabularyCompatibilityTest {

  @Test
  void marketplaceActionNamesAreCompatibleWithCommonExecutionEnum() {
    assertThat(Arrays.stream(MarketplaceExecutionActionType.values()).map(Enum::name))
        .allSatisfy(name -> assertThat(ExecutionActionTypeCode.valueOf(name)).isNotNull());
  }

  @Test
  void marketplaceResourceTypeNamesAreCompatibleWithCommonExecutionEnum() {
    assertThat(Arrays.stream(MarketplaceExecutionResourceType.values()).map(Enum::name))
        .allSatisfy(name -> assertThat(ExecutionResourceTypeCode.valueOf(name)).isNotNull());
  }

  @Test
  void marketplaceResourceStatusNamesAreCompatibleWithCommonExecutionEnum() {
    assertThat(Arrays.stream(MarketplaceExecutionResourceStatus.values()).map(Enum::name))
        .allSatisfy(name -> assertThat(ExecutionResourceStatusCode.valueOf(name)).isNotNull());
  }
}
