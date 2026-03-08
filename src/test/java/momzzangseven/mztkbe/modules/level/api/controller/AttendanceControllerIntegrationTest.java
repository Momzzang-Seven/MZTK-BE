package momzzangseven.mztkbe.modules.level.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpPolicyEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.AttendanceLogJpaRepository;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpLedgerJpaRepository;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpPolicyJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("AttendanceController 실경로 통합 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AttendanceControllerIntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired protected MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired protected ZoneId appZoneId;

  @org.springframework.beans.factory.annotation.Autowired
  protected AttendanceLogJpaRepository attendanceLogJpaRepository;

  @org.springframework.beans.factory.annotation.Autowired
  protected XpPolicyJpaRepository xpPolicyJpaRepository;

  @org.springframework.beans.factory.annotation.Autowired
  protected XpLedgerJpaRepository xpLedgerJpaRepository;

  @MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @BeforeEach
  void seedCheckInXpPolicyIfMissing() {
    LocalDateTime now = LocalDateTime.now();
    boolean hasActiveCheckInPolicy =
        !xpPolicyJpaRepository
            .findActiveByType(XpType.CHECK_IN, now, PageRequest.of(0, 1))
            .isEmpty();
    if (!hasActiveCheckInPolicy) {
      xpPolicyJpaRepository.save(
          XpPolicyEntity.builder()
              .type(XpType.CHECK_IN)
              .xpAmount(10)
              .dailyCap(1)
              .effectiveFrom(LocalDateTime.of(2000, 1, 1, 0, 0))
              .enabled(true)
              .build());
    }
  }

  @Test
  @DisplayName("출석 체크 이후 상태/주간 조회가 실제 DB 상태를 반영한다")
  void checkInAndReadAttendance_realFlow_reflectsPersistedState() throws Exception {
    Long userId = 801L;
    LocalDate today = LocalDate.now(appZoneId);

    assertThat(attendanceLogJpaRepository.existsByUserIdAndAttendedDate(userId, today)).isFalse();
    assertThat(
            xpLedgerJpaRepository.countByUserIdAndTypeAndEarnedOn(userId, XpType.CHECK_IN, today))
        .isEqualTo(0);

    mockMvc
        .perform(post("/users/me/attendance").with(userPrincipal(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.success").value(true))
        .andExpect(jsonPath("$.data.attendedDate").value(today.toString()));

    assertThat(attendanceLogJpaRepository.existsByUserIdAndAttendedDate(userId, today)).isTrue();
    assertThat(
            xpLedgerJpaRepository.countByUserIdAndTypeAndEarnedOn(userId, XpType.CHECK_IN, today))
        .isEqualTo(1);

    mockMvc
        .perform(get("/users/me/attendance/status").with(userPrincipal(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.today").value(today.toString()))
        .andExpect(jsonPath("$.data.hasAttendedToday").value(true))
        .andExpect(jsonPath("$.data.streakCount").value(1));

    mockMvc
        .perform(get("/users/me/attendance/weekly").with(userPrincipal(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.attendedCount").value(1))
        .andExpect(jsonPath("$.data.attendedDates[0]").value(today.toString()));
  }

  @Test
  @DisplayName("같은 날 중복 출석 체크는 중복 저장 없이 ALREADY_CHECKED_IN을 반환한다")
  void checkInTwice_realFlow_preventsDuplicateLog() throws Exception {
    Long userId = 802L;
    LocalDate today = LocalDate.now(appZoneId);

    mockMvc
        .perform(post("/users/me/attendance").with(userPrincipal(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.success").value(true));

    mockMvc
        .perform(post("/users/me/attendance").with(userPrincipal(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.success").value(false))
        .andExpect(jsonPath("$.data.message").value("ALREADY_CHECKED_IN"));

    assertThat(
            attendanceLogJpaRepository
                .findByUserIdAndAttendedDateBetweenOrderByAttendedDateAsc(userId, today, today)
                .size())
        .isEqualTo(1);
    assertThat(
            xpLedgerJpaRepository.countByUserIdAndTypeAndEarnedOn(userId, XpType.CHECK_IN, today))
        .isEqualTo(1);
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private RequestPostProcessor authenticatedPrincipal(Long userId, String... authorities) {
    java.util.Objects.requireNonNull(userId, "userId");
    java.util.List<SimpleGrantedAuthority> grantedAuthorities =
        Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(userId, null, grantedAuthorities);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }
}
