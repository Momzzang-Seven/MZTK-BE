package momzzangseven.mztkbe.modules.level.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.level.application.dto.GetLevelPoliciesResult;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelResult;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelUpHistoriesResult;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyXpLedgerResult;
import momzzangseven.mztkbe.modules.level.application.dto.LevelPolicyItem;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpHistoryItem;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpResult;
import momzzangseven.mztkbe.modules.level.application.dto.XpDailyCapStatusItem;
import momzzangseven.mztkbe.modules.level.application.dto.XpLedgerEntryItem;
import momzzangseven.mztkbe.modules.level.application.dto.XpPolicyItem;
import momzzangseven.mztkbe.modules.level.application.port.in.GetLevelPoliciesUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GetMyLevelUpHistoriesUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GetMyLevelUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GetMyXpLedgerUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.LevelUpUseCase;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardStatus;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxPhase;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("LevelController 컨트롤러 계약 테스트 (MockMvc + H2)")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class LevelControllerTest {

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

  @MockitoBean private GetMyLevelUseCase getMyLevelUseCase;
  @MockitoBean private GetLevelPoliciesUseCase getLevelPoliciesUseCase;
  @MockitoBean private LevelUpUseCase levelUpUseCase;
  @MockitoBean private GetMyLevelUpHistoriesUseCase getMyLevelUpHistoriesUseCase;
  @MockitoBean private GetMyXpLedgerUseCase getMyXpLedgerUseCase;

  @Test
  @DisplayName("GET /users/me/level 성공")
  void getMyLevel_success() throws Exception {
    given(getMyLevelUseCase.execute(1L))
        .willReturn(
            GetMyLevelResult.builder()
                .level(3)
                .availableXp(120)
                .requiredXpForNext(80)
                .rewardMztkForNext(30)
                .build());

    mockMvc
        .perform(get("/users/me/level").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.level").value(3))
        .andExpect(jsonPath("$.data.availableXp").value(120));
  }

  @Test
  @DisplayName("GET /users/me/level 인증 없으면 401")
  void getMyLevel_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/users/me/level")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /users/me/level 인증 principal이 null이면 401")
  void getMyLevel_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(get("/users/me/level").with(nullUserPrincipal()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /levels/policies 성공")
  void getPolicies_success() throws Exception {
    given(getLevelPoliciesUseCase.execute())
        .willReturn(
            GetLevelPoliciesResult.builder()
                .levelPolicies(
                    List.of(
                        LevelPolicyItem.builder()
                            .currentLevel(1)
                            .toLevel(2)
                            .requiredXp(100)
                            .rewardMztk(10)
                            .build()))
                .xpPolicies(
                    List.of(
                        XpPolicyItem.builder()
                            .type(XpType.CHECK_IN)
                            .xpAmount(10)
                            .dailyCap(1)
                            .build()))
                .build());

    mockMvc
        .perform(get("/levels/policies").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.levelPolicies[0].currentLevel").value(1))
        .andExpect(jsonPath("$.data.xpPolicies[0].type").value("CHECK_IN"));
  }

  @Test
  @DisplayName("GET /levels/policies 인증 없으면 401")
  void getPolicies_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/levels/policies")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /users/me/level-ups 성공")
  void levelUp_success() throws Exception {
    given(levelUpUseCase.execute(any(LevelUpCommand.class)))
        .willReturn(
            LevelUpResult.builder()
                .levelUpHistoryId(100L)
                .fromLevel(1)
                .toLevel(2)
                .spentXp(100)
                .rewardMztk(10)
                .rewardStatus(RewardStatus.PENDING)
                .rewardTxStatus(RewardTxStatus.CREATED)
                .rewardTxPhase(RewardTxPhase.PENDING)
                .rewardTxHash(null)
                .rewardExplorerUrl(null)
                .build());

    mockMvc
        .perform(post("/users/me/level-ups").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.levelUpHistoryId").value(100))
        .andExpect(jsonPath("$.data.toLevel").value(2));
  }

  @Test
  @DisplayName("POST /users/me/level-ups 인증 없으면 401")
  void levelUp_unauthenticated_returns401() throws Exception {
    mockMvc.perform(post("/users/me/level-ups")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /users/me/level-ups principal이 null이면 401")
  void levelUp_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(post("/users/me/level-ups").with(nullUserPrincipal()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /users/me/level-up-histories 기본 페이지 파라미터로 호출")
  void getHistories_defaultPaging_success() throws Exception {
    given(getMyLevelUpHistoriesUseCase.execute(eq(1L), eq(0), eq(20)))
        .willReturn(
            GetMyLevelUpHistoriesResult.builder()
                .page(0)
                .size(20)
                .hasNext(false)
                .histories(
                    List.of(
                        LevelUpHistoryItem.builder()
                            .levelUpHistoryId(9L)
                            .fromLevel(1)
                            .toLevel(2)
                            .spentXp(100)
                            .rewardMztk(10)
                            .rewardStatus(RewardStatus.SUCCESS)
                            .rewardTxStatus(RewardTxStatus.SUCCEEDED)
                            .rewardTxPhase(RewardTxPhase.SUCCESS)
                            .createdAt(LocalDateTime.now())
                            .build()))
                .build());

    mockMvc
        .perform(get("/users/me/level-up-histories").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.histories[0].levelUpHistoryId").value(9));

    verify(getMyLevelUpHistoriesUseCase).execute(1L, 0, 20);
  }

  @Test
  @DisplayName("GET /users/me/level-up-histories 인증 없으면 401")
  void getHistories_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/users/me/level-up-histories")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /users/me/level-up-histories principal이 null이면 401")
  void getHistories_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(get("/users/me/level-up-histories").with(nullUserPrincipal()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /users/me/xp-ledger 사용자 페이지 파라미터를 전달한다")
  void getXpLedger_customPaging_success() throws Exception {
    given(getMyXpLedgerUseCase.execute(eq(1L), eq(2), eq(5)))
        .willReturn(
            GetMyXpLedgerResult.builder()
                .page(2)
                .size(5)
                .hasNext(true)
                .earnedOn(LocalDate.now())
                .entries(
                    List.of(
                        XpLedgerEntryItem.builder()
                            .xpLedgerId(1L)
                            .type(XpType.POST)
                            .xpAmount(15)
                            .earnedOn(LocalDate.now())
                            .occurredAt(LocalDateTime.now())
                            .idempotencyKey("k1")
                            .sourceRef("post:1")
                            .createdAt(LocalDateTime.now())
                            .build()))
                .todayCaps(
                    List.of(
                        XpDailyCapStatusItem.builder()
                            .type(XpType.POST)
                            .dailyCap(3)
                            .grantedCount(1)
                            .remainingCount(2)
                            .build()))
                .build());

    mockMvc
        .perform(get("/users/me/xp-ledger?page=2&size=5").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.size").value(5))
        .andExpect(jsonPath("$.data.entries[0].type").value("POST"));

    verify(getMyXpLedgerUseCase).execute(1L, 2, 5);
  }

  @Test
  @DisplayName("GET /users/me/xp-ledger 인증 없으면 401")
  void getXpLedger_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/users/me/xp-ledger")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /users/me/xp-ledger principal이 null이면 401")
  void getXpLedger_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(get("/users/me/xp-ledger").with(nullUserPrincipal()))
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
