package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=true",
      "web3.eip7702.enabled=true",
      "web3.qna.admin.enabled=false"
    })
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("QnaAdminEscrowController 비활성화 계약 테스트")
class QnaAdminEscrowControllerDisabledTest {

  @org.springframework.beans.factory.annotation.Autowired protected MockMvc mockMvc;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.qna.infrastructure.config
          .QnaAutoAcceptConfigurationValidator
      qnaAutoAcceptConfigurationValidator;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @Test
  @DisplayName("qna admin feature flag 비활성화 시 endpoint는 404")
  void qnaAdminEndpoint_disabledByAdminFlag_returns404() throws Exception {
    mockMvc
        .perform(
            get("/admin/web3/qna/questions/101/answers/201/settlement-review")
                .with(adminPrincipal(1L)))
        .andExpect(status().isNotFound());
  }

  private RequestPostProcessor adminPrincipal(Long userId) {
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(
            userId, null, Arrays.asList(new SimpleGrantedAuthority("ROLE_ADMIN")));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }
}
