package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ProvisionTreasuryKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ArchiveTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.DisableTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=true",
      "web3.reward-token.treasury.provisioning.enabled=true"
    })
@DisplayName("TreasuryKeyController 컨트롤러 계약 테스트 (MockMvc + H2)")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class TreasuryKeyControllerTest {

  @org.springframework.beans.factory.annotation.Autowired
  protected org.springframework.test.web.servlet.MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @MockitoBean private ProvisionTreasuryKeyUseCase provisionTreasuryKeyUseCase;
  @MockitoBean private LoadTreasuryWalletUseCase loadTreasuryWalletUseCase;
  @MockitoBean private DisableTreasuryWalletUseCase disableTreasuryWalletUseCase;
  @MockitoBean private ArchiveTreasuryWalletUseCase archiveTreasuryWalletUseCase;

  @Test
  @DisplayName("POST /admin/web3/treasury-keys/provision 성공")
  void provision_success() throws Exception {
    String address = "0xaec2962556aa2c9c3b3e873121cb4c61ae5f1823";
    given(provisionTreasuryKeyUseCase.execute(any(ProvisionTreasuryKeyCommand.class)))
        .willReturn(
            new ProvisionTreasuryKeyResult(
                "reward-treasury",
                TreasuryRole.REWARD,
                "kms-key-id",
                address,
                TreasuryWalletStatus.ACTIVE,
                TreasuryKeyOrigin.IMPORTED,
                LocalDateTime.parse("2026-01-01T00:00:00")));

    mockMvc
        .perform(
            post("/admin/web3/treasury-keys/provision")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "rawPrivateKey",
                            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "role",
                            "REWARD",
                            "expectedAddress",
                            address))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.kmsKeyId").value("kms-key-id"))
        .andExpect(jsonPath("$.data.walletAddress").value(address));
  }

  @Test
  @DisplayName("POST /admin/web3/treasury-keys/provision USER 권한이면 403")
  void provision_forbiddenForUser_returns403() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/treasury-keys/provision")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "rawPrivateKey",
                            "0xabc",
                            "role",
                            "REWARD",
                            "expectedAddress",
                            "0xabc"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /admin/web3/treasury-keys/provision 인증 없으면 401")
  void provision_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(post("/admin/web3/treasury-keys/provision"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /admin/web3/treasury-keys/provision 키 공백이면 400")
  void provision_blankPrivateKey_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/treasury-keys/provision")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "rawPrivateKey",
                            " ",
                            "role",
                            "REWARD",
                            "expectedAddress",
                            "0x" + "a".repeat(40)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));
  }

  @Test
  @DisplayName("GET /admin/web3/treasury-keys/{alias} ADMIN 성공")
  void get_success_returnsView() throws Exception {
    String address = "0xaec2962556aa2c9c3b3e873121cb4c61ae5f1823";
    given(loadTreasuryWalletUseCase.execute(eq("reward-treasury"), anyLong()))
        .willReturn(
            Optional.of(
                new TreasuryWalletView(
                    "reward-treasury",
                    TreasuryRole.REWARD,
                    "kms-key-id",
                    address,
                    TreasuryWalletStatus.ACTIVE,
                    TreasuryKeyOrigin.IMPORTED,
                    LocalDateTime.parse("2026-01-01T00:00:00"),
                    null)));

    mockMvc
        .perform(get("/admin/web3/treasury-keys/reward-treasury").with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.kmsKeyId").value("kms-key-id"))
        .andExpect(jsonPath("$.data.walletAddress").value(address));
  }

  @Test
  @DisplayName("GET /admin/web3/treasury-keys/{alias} USER 권한이면 403")
  void get_forbiddenForUser_returns403() throws Exception {
    mockMvc
        .perform(get("/admin/web3/treasury-keys/reward-treasury").with(userPrincipal(1L)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("GET /admin/web3/treasury-keys/{alias} 인증 없으면 401")
  void get_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/admin/web3/treasury-keys/reward-treasury"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /admin/web3/treasury-keys/{alias}/disable USER 권한이면 403")
  void disable_forbiddenForUser_returns403() throws Exception {
    mockMvc
        .perform(post("/admin/web3/treasury-keys/reward-treasury/disable").with(userPrincipal(1L)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /admin/web3/treasury-keys/{alias}/archive USER 권한이면 403")
  void archive_forbiddenForUser_returns403() throws Exception {
    mockMvc
        .perform(post("/admin/web3/treasury-keys/reward-treasury/archive").with(userPrincipal(1L)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /admin/web3/treasury-keys/provision principal이 null이면 401")
  void provision_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/treasury-keys/provision")
                .with(nullAdminPrincipal())
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "rawPrivateKey",
                            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "role",
                            "REWARD",
                            "expectedAddress",
                            "0x" + "a".repeat(40)))))
        .andExpect(status().isUnauthorized());
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor userPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor adminPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor stepUpPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER", "ROLE_STEP_UP");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullUserPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullAdminPrincipal() {
    return nullPrincipalWithRoles("ROLE_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullStepUpPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER", "ROLE_STEP_UP");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullPrincipalWithRoles(
      String... authorities) {
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            java.util.Arrays.stream(authorities)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            null, null, grantedAuthorities);
    org.springframework.security.core.context.SecurityContext context =
        org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedPrincipal(
      Long userId, String... authorities) {
    java.util.Objects.requireNonNull(userId, "userId");
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            java.util.Arrays.stream(authorities)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userId, null, grantedAuthorities);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(token);
  }

  private String json(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }
}
