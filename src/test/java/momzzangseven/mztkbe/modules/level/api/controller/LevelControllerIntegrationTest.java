package momzzangseven.mztkbe.modules.level.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Arrays;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.LevelPolicyEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpPolicyEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.LevelPolicyJpaRepository;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.UserProgressJpaRepository;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpPolicyJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("LevelController 실경로 통합 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LevelControllerIntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired protected MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @org.springframework.beans.factory.annotation.Autowired
  protected UserProgressJpaRepository userProgressJpaRepository;

  @org.springframework.beans.factory.annotation.Autowired
  protected LevelPolicyJpaRepository levelPolicyJpaRepository;

  @org.springframework.beans.factory.annotation.Autowired
  protected XpPolicyJpaRepository xpPolicyJpaRepository;

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
  void seedPoliciesIfMissing() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime baseline = LocalDateTime.of(2000, 1, 1, 0, 0);

    if (levelPolicyJpaRepository.findActivePolicies(now).isEmpty()) {
      levelPolicyJpaRepository.save(
          LevelPolicyEntity.builder()
              .level(1)
              .requiredXp(100)
              .rewardMztk(10)
              .effectiveFrom(baseline)
              .enabled(true)
              .build());
    }

    if (xpPolicyJpaRepository.findActivePolicies(now).isEmpty()) {
      xpPolicyJpaRepository.save(
          XpPolicyEntity.builder()
              .type(XpType.CHECK_IN)
              .xpAmount(10)
              .dailyCap(1)
              .effectiveFrom(baseline)
              .enabled(true)
              .build());
    }
  }

  @Test
  @DisplayName("GET /levels/policies는 활성 정책을 실제 DB에서 조회한다")
  void getPolicies_realFlow_returnsPoliciesFromH2() throws Exception {
    mockMvc
        .perform(get("/levels/policies").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.levelPolicies").isArray())
        .andExpect(jsonPath("$.data.levelPolicies[0].currentLevel").exists())
        .andExpect(jsonPath("$.data.xpPolicies").isArray())
        .andExpect(jsonPath("$.data.xpPolicies[0].type").exists());
  }

  @Test
  @DisplayName("GET /users/me/level 호출 시 user_progress가 없으면 생성된다")
  void getMyLevel_realFlow_createsUserProgressWhenMissing() throws Exception {
    Long userId = 701L;
    assertThat(userProgressJpaRepository.findById(userId)).isEmpty();

    mockMvc
        .perform(get("/users/me/level").with(userPrincipal(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.level").value(1))
        .andExpect(jsonPath("$.data.availableXp").value(0));

    var saved = userProgressJpaRepository.findById(userId).orElseThrow();
    assertThat(saved.getLevel()).isEqualTo(1);
    assertThat(saved.getAvailableXp()).isEqualTo(0);
    assertThat(saved.getLifetimeXp()).isEqualTo(0);
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
