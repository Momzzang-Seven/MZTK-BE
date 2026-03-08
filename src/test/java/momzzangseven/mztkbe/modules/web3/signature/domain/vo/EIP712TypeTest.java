package momzzangseven.mztkbe.modules.web3.signature.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EIP712TypeTest {

  @Test
  void constants_matchExpectedDefinitions() {
    assertThat(EIP712Type.EIP712_DOMAIN_TYPE).contains("EIP712Domain(");
    assertThat(EIP712Type.AUTH_REQUEST_TYPE).contains("AuthRequest(");
    assertThat(EIP712Type.FULL_AUTH_REQUEST_TYPE).isEqualTo(EIP712Type.AUTH_REQUEST_TYPE);
  }
}
