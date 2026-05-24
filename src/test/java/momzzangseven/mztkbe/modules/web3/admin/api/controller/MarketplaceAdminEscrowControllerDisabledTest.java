package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
      "web3.eip7702.enabled=false",
      "web3.execution.internal.enabled=true",
      "web3.marketplace.admin.enabled=false"
    })
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("MarketplaceAdminEscrowController feature flag disabled 계약 테스트")
class MarketplaceAdminEscrowControllerDisabledTest {

  @Autowired private MockMvc mockMvc;

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
  @DisplayName("feature flag disabled 시 refund review endpoint 는 노출되지 않는다")
  void refundReviewEndpoint_disabledByMarketplaceAdminFlag_returns404() throws Exception {
    mockMvc
        .perform(
            get("/admin/web3/marketplace/reservations/77/refund-review").with(adminPrincipal(9L)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("feature flag disabled 시 refund execute endpoint 는 노출되지 않는다")
  void refundEndpoint_disabledByMarketplaceAdminFlag_returns404() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/marketplace/reservations/77/refund")
                .with(adminPrincipal(9L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reasonCode": "TRAINER_TIMEOUT",
                      "memo": "memo",
                      "confirmManualRefund": false
                    }
                    """))
        .andExpect(status().isNotFound());
  }

  private RequestPostProcessor adminPrincipal(Long userId) {
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }
}
