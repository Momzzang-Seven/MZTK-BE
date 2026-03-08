package momzzangseven.mztkbe.modules.web3.transfer.application.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import org.junit.jupiter.api.Test;

class UnsupportedDomainRewardResolverTest {

  private final UnsupportedDomainRewardResolver resolver = new UnsupportedDomainRewardResolver();

  @Test
  void supports_returnsTrue_forCurrentlyUnsupportedTypes() {
    assertThat(resolver.supports(DomainReferenceType.LEVEL_UP_REWARD)).isTrue();
    assertThat(resolver.supports(DomainReferenceType.QUESTION_REWARD)).isFalse();
  }

  @Test
  void isFallback_returnsTrue() {
    assertThat(resolver.isFallback()).isTrue();
  }

  @Test
  void resolve_throwsAlways() {
    assertThatThrownBy(() -> resolver.resolve(1L, "1"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("domain resolver is not available");
  }
}
