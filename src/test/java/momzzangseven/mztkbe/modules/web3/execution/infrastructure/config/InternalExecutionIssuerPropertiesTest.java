package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.InternalExecutionActionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("InternalExecutionIssuerProperties binding test")
class InternalExecutionIssuerPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(InternalExecutionIssuerProperties.class);

  @Test
  @DisplayName("new internal prefix를 우선 바인딩한다")
  void bindsNewInternalPrefix() {
    contextRunner
        .withPropertyValues(
            "web3.execution.internal.enabled=true",
            "web3.execution.internal.batch-size=31",
            "web3.execution.internal.eip1559-ttl-seconds=123",
            "web3.execution.internal.signer.wallet-alias=internal-signer",
            "web3.execution.internal.signer.key-encryption-key-b64=test-key",
            "web3.execution.internal.action-policy=QNA_AND_MARKETPLACE_ADMIN")
        .run(
            context -> {
              InternalExecutionIssuerProperties properties =
                  context.getBean(InternalExecutionIssuerProperties.class);

              assertThat(properties.getEnabled()).isTrue();
              assertThat(properties.getBatchSize()).isEqualTo(31);
              assertThat(properties.getEip1559TtlSeconds()).isEqualTo(123);
              assertThat(properties.getSigner().getWalletAlias()).isEqualTo("internal-signer");
              assertThat(properties.getSigner().getKeyEncryptionKeyB64()).isEqualTo("test-key");
              assertThat(properties.getActionPolicy())
                  .isEqualTo(InternalExecutionActionPolicy.QNA_AND_MARKETPLACE_ADMIN);
              assertThat(properties.getActionTypes())
                  .containsExactly(
                      ExecutionActionType.QNA_ADMIN_SETTLE,
                      ExecutionActionType.QNA_ADMIN_REFUND,
                      ExecutionActionType.MARKETPLACE_ADMIN_SETTLE,
                      ExecutionActionType.MARKETPLACE_ADMIN_REFUND);
            });
  }

  @Test
  @DisplayName("필수 enable만 주면 나머지 internal 설정은 기본값을 사용한다")
  void usesDefaultsWhenOnlyInternalEnabledProvided() {
    contextRunner
        .withPropertyValues("web3.execution.internal.enabled=true")
        .run(
            context -> {
              InternalExecutionIssuerProperties properties =
                  context.getBean(InternalExecutionIssuerProperties.class);

              assertThat(properties.getEnabled()).isTrue();
              assertThat(properties.getBatchSize()).isEqualTo(20);
              assertThat(properties.getCron()).isEqualTo("0/10 * * * * *");
              assertThat(properties.getZone()).isEqualTo("Asia/Seoul");
              assertThat(properties.getEip1559TtlSeconds()).isEqualTo(90);
              assertThat(properties.getSigner().getWalletAlias()).isNull();
              assertThat(properties.getSigner().getKeyEncryptionKeyB64()).isNull();
              assertThat(properties.getActionPolicy())
                  .isEqualTo(InternalExecutionActionPolicy.QNA_ADMIN);
              assertThat(properties.getActionTypes())
                  .containsExactly(
                      ExecutionActionType.QNA_ADMIN_SETTLE, ExecutionActionType.QNA_ADMIN_REFUND);
            });
  }
}
