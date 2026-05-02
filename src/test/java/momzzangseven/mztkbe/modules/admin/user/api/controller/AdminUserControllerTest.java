package momzzangseven.mztkbe.modules.admin.user.api.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.admin.user.application.dto.AdminUserListItemResult;
import momzzangseven.mztkbe.modules.admin.user.application.dto.GetAdminUsersCommand;
import momzzangseven.mztkbe.modules.admin.user.application.port.in.GetAdminUsersUseCase;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@DisplayName("AdminUserController 컨트롤러 계약 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerTest {

  @org.springframework.beans.factory.annotation.Autowired
  protected org.springframework.test.web.servlet.MockMvc mockMvc;

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

  @MockitoBean private GetAdminUsersUseCase getAdminUsersUseCase;

  @Test
  @DisplayName("GET /admin/users ADMIN 이면 200과 페이지 응답을 반환한다")
  void getUsers_admin_returns200() throws Exception {
    given(
            getAdminUsersUseCase.execute(
                org.mockito.ArgumentMatchers.any(GetAdminUsersCommand.class)))
        .willReturn(
            new PageImpl<>(
                List.of(
                    new AdminUserListItemResult(
                        21L,
                        "alpha",
                        UserRole.USER,
                        "alpha@example.com",
                        Instant.parse("2025-01-10T11:22:33Z"),
                        AccountStatus.ACTIVE,
                        3L,
                        4L))));

    mockMvc
        .perform(get("/admin/users").with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content[0].userId").value(21))
        .andExpect(jsonPath("$.data.content[0].nickname").value("alpha"))
        .andExpect(jsonPath("$.data.content[0].role").value("USER"))
        .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.content[0].postCount").value(3))
        .andExpect(jsonPath("$.data.content[0].commentCount").value(4));
  }

  @Test
  @DisplayName("GET /admin/users whitelist 밖 sort 값이면 400")
  void getUsers_invalidSort_returns400() throws Exception {
    mockMvc
        .perform(get("/admin/users").param("sort", "createdAt").with(adminPrincipal(9L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /admin/users role=ADMIN 이면 400")
  void getUsers_adminRoleFilter_returns400() throws Exception {
    mockMvc
        .perform(get("/admin/users").param("role", "ADMIN").with(adminPrincipal(9L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /admin/users search 길이가 100자를 초과하면 400")
  void getUsers_tooLongSearch_returns400() throws Exception {
    mockMvc
        .perform(get("/admin/users").param("search", "a".repeat(101)).with(adminPrincipal(9L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /admin/users USER 권한이면 403")
  void getUsers_userForbidden_returns403() throws Exception {
    mockMvc.perform(get("/admin/users").with(userPrincipal(1L))).andExpect(status().isForbidden());
  }

  private RequestPostProcessor adminPrincipal(Long userId) {
    return SecurityMockMvcRequestPostProcessors.authentication(
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userId, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN_GENERATED"))));
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    return SecurityMockMvcRequestPostProcessors.authentication(
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userId, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }
}
