package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=true",
      "web3.eip7702.enabled=false",
      "web3.execution.internal-issuer.enabled=false"
    })
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("QnaAdminEscrowController internal issuer 비활성화 시 설정 오류 계약 테스트")
class QnaAdminEscrowInternalIssuerDisabledControllerTest {

  private static final String INTERNAL_ISSUER_DISABLED_CODE = "WEB3_015";
  private static final String INTERNAL_ISSUER_DISABLED_MESSAGE =
      "QnA admin execution requires web3.execution.internal-issuer.enabled=true";

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
  @DisplayName("internal issuer 비활성화 시 settlement review endpoint는 설정 오류를 응답한다")
  void settlementReviewEndpoint_disabledByInternalIssuerFlag_returnsBusinessError()
      throws Exception {
    assertInternalIssuerDisabled(
        get("/admin/web3/qna/questions/101/answers/201/settlement-review")
            .with(adminPrincipal(1L)));
  }

  @Test
  @DisplayName("internal issuer 비활성화 시 settlement endpoint는 설정 오류를 응답한다")
  void settlementEndpoint_disabledByInternalIssuerFlag_returnsBusinessError() throws Exception {
    assertInternalIssuerDisabled(
        post("/admin/web3/qna/questions/101/answers/201/settle").with(adminPrincipal(1L)));
  }

  @Test
  @DisplayName("internal issuer 비활성화 시 refund review endpoint는 설정 오류를 응답한다")
  void refundReviewEndpoint_disabledByInternalIssuerFlag_returnsBusinessError() throws Exception {
    assertInternalIssuerDisabled(
        get("/admin/web3/qna/questions/101/refund-review").with(adminPrincipal(1L)));
  }

  @Test
  @DisplayName("internal issuer 비활성화 시 refund endpoint는 설정 오류를 응답한다")
  void refundEndpoint_disabledByInternalIssuerFlag_returnsBusinessError() throws Exception {
    assertInternalIssuerDisabled(
        post("/admin/web3/qna/questions/101/refund").with(adminPrincipal(1L)));
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

  private void assertInternalIssuerDisabled(MockHttpServletRequestBuilder request)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value(INTERNAL_ISSUER_DISABLED_CODE))
        .andExpect(jsonPath("$.message").value(INTERNAL_ISSUER_DISABLED_MESSAGE));
  }
}
