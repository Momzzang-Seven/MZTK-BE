package momzzangseven.mztkbe.modules.web3.eip7702.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareEip7702AuthorizationCommand;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareEip7702AuthorizationResult;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrepareEip7702AuthorizationService unit test")
class PrepareEip7702AuthorizationServiceTest {

  private static final String AUTHORITY = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String DELEGATE = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
  private static final String HASH = "0x" + "f".repeat(64);

  @Mock private Eip7702ChainPort eip7702ChainPort;
  @Mock private Eip7702AuthorizationPort eip7702AuthorizationPort;

  private PrepareEip7702AuthorizationService service;

  @BeforeEach
  void setUp() {
    service = new PrepareEip7702AuthorizationService(eip7702ChainPort, eip7702AuthorizationPort);
  }

  @Test
  void execute_buildsAuthorizationHashWithPendingAuthorityNonce() {
    when(eip7702ChainPort.loadPendingAccountNonce(AUTHORITY)).thenReturn(BigInteger.valueOf(12L));
    when(eip7702AuthorizationPort.buildSigningHashHex(10L, DELEGATE, BigInteger.valueOf(12L)))
        .thenReturn(HASH);

    PrepareEip7702AuthorizationResult result =
        service.execute(new PrepareEip7702AuthorizationCommand(10L, DELEGATE, AUTHORITY));

    assertThat(result.authorityNonce()).isEqualTo(12L);
    assertThat(result.authorizationPayloadHash()).isEqualTo(HASH);
    verify(eip7702AuthorizationPort).buildSigningHashHex(10L, DELEGATE, BigInteger.valueOf(12L));
  }

  @Test
  void execute_throwsInvalidInput_whenAuthorityNonceOverflowsLong() {
    when(eip7702ChainPort.loadPendingAccountNonce(AUTHORITY))
        .thenReturn(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));

    assertThatThrownBy(
            () -> service.execute(new PrepareEip7702AuthorizationCommand(10L, DELEGATE, AUTHORITY)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("authority nonce overflow");
    verifyNoInteractions(eip7702AuthorizationPort);
  }
}
