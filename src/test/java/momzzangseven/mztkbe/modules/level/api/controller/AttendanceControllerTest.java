package momzzangseven.mztkbe.modules.level.api.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import momzzangseven.mztkbe.modules.level.application.dto.CheckInResult;
import momzzangseven.mztkbe.modules.level.application.dto.GetAttendanceStatusResult;
import momzzangseven.mztkbe.modules.level.application.dto.GetWeeklyAttendanceResult;
import momzzangseven.mztkbe.modules.level.application.port.in.CheckInUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GetAttendanceStatusUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GetWeeklyAttendanceUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("AttendanceController 컨트롤러 계약 테스트 (MockMvc + H2)")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class AttendanceControllerTest {

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

  @MockitoBean private CheckInUseCase checkInUseCase;
  @MockitoBean private GetAttendanceStatusUseCase getAttendanceStatusUseCase;
  @MockitoBean private GetWeeklyAttendanceUseCase getWeeklyAttendanceUseCase;

  @Test
  @DisplayName("POST /users/me/attendance 성공")
  void checkIn_success() throws Exception {
    given(checkInUseCase.execute(1L)).willReturn(CheckInResult.success(LocalDate.now(), 10, 0, 2));

    mockMvc
        .perform(post("/users/me/attendance").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.success").value(true))
        .andExpect(jsonPath("$.data.grantedXp").value(10));
  }

  @Test
  @DisplayName("POST /users/me/attendance 인증 없으면 401")
  void checkIn_unauthenticated_returns401() throws Exception {
    mockMvc.perform(post("/users/me/attendance")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /users/me/attendance 인증 principal이 null이면 401")
  void checkIn_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(post("/users/me/attendance").with(nullUserPrincipal()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /users/me/attendance/status 성공")
  void getStatus_success() throws Exception {
    given(getAttendanceStatusUseCase.execute(1L))
        .willReturn(GetAttendanceStatusResult.of(LocalDate.now(), true, 5));

    mockMvc
        .perform(get("/users/me/attendance/status").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.hasAttendedToday").value(true))
        .andExpect(jsonPath("$.data.streakCount").value(5));
  }

  @Test
  @DisplayName("GET /users/me/attendance/status 인증 없으면 401")
  void getStatus_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/users/me/attendance/status")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /users/me/attendance/status principal이 null이면 401")
  void getStatus_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(get("/users/me/attendance/status").with(nullUserPrincipal()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /users/me/attendance/weekly 성공")
  void getWeekly_success() throws Exception {
    LocalDate start = LocalDate.now().minusDays(6);
    LocalDate end = LocalDate.now();
    given(getWeeklyAttendanceUseCase.execute(1L))
        .willReturn(GetWeeklyAttendanceResult.of(start, end, List.of(start.plusDays(2), end)));

    mockMvc
        .perform(get("/users/me/attendance/weekly").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.attendedCount").value(2))
        .andExpect(jsonPath("$.data.range.from").value(start.toString()))
        .andExpect(jsonPath("$.data.range.to").value(end.toString()));
  }

  @Test
  @DisplayName("GET /users/me/attendance/weekly 인증 없으면 401")
  void getWeekly_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/users/me/attendance/weekly")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /users/me/attendance/weekly principal이 null이면 401")
  void getWeekly_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(get("/users/me/attendance/weekly").with(nullUserPrincipal()))
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
