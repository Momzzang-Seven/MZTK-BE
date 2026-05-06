package momzzangseven.mztkbe.modules.admin.dashboard.api.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import momzzangseven.mztkbe.modules.admin.dashboard.application.dto.AdminBoardStatsResult;
import momzzangseven.mztkbe.modules.admin.dashboard.application.dto.AdminUserStatsResult;
import momzzangseven.mztkbe.modules.admin.dashboard.application.port.in.GetAdminBoardStatsUseCase;
import momzzangseven.mztkbe.modules.admin.dashboard.application.port.in.GetAdminUserStatsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@DisplayName("AdminDashboardController 컨트롤러 계약 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardControllerTest {

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

  @MockitoBean private GetAdminUserStatsUseCase getAdminUserStatsUseCase;
  @MockitoBean private GetAdminBoardStatsUseCase getAdminBoardStatsUseCase;

  @Test
  @DisplayName("GET /admin/dashboard/user-stats ADMIN 이면 200과 응답 필드를 반환한다")
  void getUserStats_admin_returns200() throws Exception {
    given(getAdminUserStatsUseCase.execute(9L))
        .willReturn(new AdminUserStatsResult(30L, 21L, 4L, Map.of("USER", 17L, "TRAINER", 3L)));

    mockMvc
        .perform(get("/admin/dashboard/user-stats").with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.totalUserCount").value(30))
        .andExpect(jsonPath("$.data.activeUserCount").value(21))
        .andExpect(jsonPath("$.data.blockedUserCount").value(4))
        .andExpect(jsonPath("$.data.roleCounts.USER").value(17))
        .andExpect(jsonPath("$.data.roleCounts.TRAINER").value(3));
  }

  @Test
  @DisplayName("GET /admin/dashboard/user-stats USER 권한이면 403")
  void getUserStats_userForbidden_returns403() throws Exception {
    mockMvc
        .perform(get("/admin/dashboard/user-stats").with(userPrincipal(1L)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("GET /admin/dashboard/user-stats 인증 없으면 401")
  void getUserStats_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/admin/dashboard/user-stats")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /admin/dashboard/post-stats ADMIN 이면 게시판 제재 통계를 반환한다")
  void getPostStats_admin_returns200() throws Exception {
    given(getAdminBoardStatsUseCase.execute(9L))
        .willReturn(
            new AdminBoardStatsResult(
                Map.of("SPAM", 2L, "OTHER", 0L),
                Map.of("FREE", 2L, "QUESTION", 0L),
                Map.of("POST", 0L, "COMMENT", 2L)));

    mockMvc
        .perform(get("/admin/dashboard/post-stats").with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postRemovalReasonStats.SPAM").value(2))
        .andExpect(jsonPath("$.data.boardTypeSplit.FREE").value(2))
        .andExpect(jsonPath("$.data.targetTypeStats.COMMENT").value(2));
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
