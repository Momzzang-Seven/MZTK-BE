package momzzangseven.mztkbe.modules.user.api.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.UserAccountEntity;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.repository.UserAccountJpaRepository;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.UserProgressEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.UserProgressJpaRepository;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("UserLeaderboardController 실경로 통합 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserLeaderboardControllerIntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired
  protected org.springframework.test.web.servlet.MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected UserJpaRepository userJpaRepository;

  @org.springframework.beans.factory.annotation.Autowired
  protected UserAccountJpaRepository userAccountJpaRepository;

  @org.springframework.beans.factory.annotation.Autowired
  protected UserProgressJpaRepository userProgressJpaRepository;

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
  @DisplayName("공개 리더보드는 정렬, 제한, 계정 상태 필터, 관리자 제외, progress 기본값을 모두 반영한다")
  void getUserLeaderboard_realFlow_appliesOrderingAndFilters() throws Exception {
    long[] expectedIds = seedLeaderboardUsers();

    mockMvc
        .perform(get("/users/leaderboard"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.users.length()").value(10))
        .andExpect(jsonPath("$.data.users[0].rank").value(1))
        .andExpect(jsonPath("$.data.users[0].userId").value(expectedIds[1]))
        .andExpect(jsonPath("$.data.users[0].level").value(9))
        .andExpect(jsonPath("$.data.users[0].lifetimeXp").value(1000))
        .andExpect(jsonPath("$.data.users[1].userId").value(expectedIds[0]))
        .andExpect(jsonPath("$.data.users[1].lifetimeXp").value(900))
        .andExpect(jsonPath("$.data.users[2].userId").value(expectedIds[2]))
        .andExpect(jsonPath("$.data.users[2].lifetimeXp").value(900))
        .andExpect(jsonPath("$.data.users[3].userId").value(expectedIds[3]))
        .andExpect(jsonPath("$.data.users[4].userId").value(expectedIds[4]))
        .andExpect(jsonPath("$.data.users[5].userId").value(expectedIds[5]))
        .andExpect(jsonPath("$.data.users[6].userId").value(expectedIds[6]))
        .andExpect(jsonPath("$.data.users[7].userId").value(expectedIds[7]))
        .andExpect(jsonPath("$.data.users[8].userId").value(expectedIds[8]))
        .andExpect(jsonPath("$.data.users[9].userId").value(expectedIds[9]))
        .andExpect(jsonPath("$.data.users[1].rank").value(2))
        .andExpect(jsonPath("$.data.users[2].rank").value(3))
        .andExpect(jsonPath("$.data.users[9].rank").value(10))
        .andExpect(content().string(not(containsString("\"nickname\":\"deleted\""))))
        .andExpect(content().string(not(containsString("\"nickname\":\"blocked\""))))
        .andExpect(content().string(not(containsString("\"nickname\":\"unverified\""))))
        .andExpect(content().string(not(containsString("\"nickname\":\"admin\""))));
  }

  @Test
  @DisplayName("리더보드 대상이 10명 미만이면 전체를 반환하고 progress 없는 유저는 기본값을 사용한다")
  void getUserLeaderboard_whenLessThanTen_returnsAllAndDefaultsMissingProgress() throws Exception {
    Long noProgressUserId = saveUser("missing-progress@example.com", "missing", UserRole.USER);
    saveActiveAccount(noProgressUserId);

    Long trainerId = saveUser("trainer@example.com", "trainer", UserRole.TRAINER);
    saveActiveAccount(trainerId);
    saveProgress(trainerId, 3, 200, 15);

    mockMvc
        .perform(get("/users/leaderboard"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.users.length()").value(2))
        .andExpect(jsonPath("$.data.users[0].userId").value(trainerId))
        .andExpect(jsonPath("$.data.users[1].userId").value(noProgressUserId))
        .andExpect(jsonPath("$.data.users[1].level").value(1))
        .andExpect(jsonPath("$.data.users[1].lifetimeXp").value(0));
  }

  private long[] seedLeaderboardUsers() {
    long[] expectedIds = new long[12];
    for (int i = 0; i < 12; i++) {
      expectedIds[i] = saveUser("user" + (i + 1) + "@example.com", "user" + (i + 1), UserRole.USER);
      saveActiveAccount(expectedIds[i]);
    }

    saveProgress(expectedIds[0], 9, 900, 30);
    saveProgress(expectedIds[1], 9, 1000, 10);
    saveProgress(expectedIds[2], 9, 900, 10);
    saveProgress(expectedIds[3], 9, 900, 10);
    saveProgress(expectedIds[4], 8, 700, 5);
    saveProgress(expectedIds[5], 7, 500, 20);
    saveProgress(expectedIds[6], 6, 300, 30);
    saveProgress(expectedIds[7], 5, 200, 0);
    saveProgress(expectedIds[8], 4, 100, 0);
    saveProgress(expectedIds[9], 3, 50, 0);
    saveProgress(expectedIds[10], 2, 10, 0);
    // expectedIds[11] intentionally has no progress row

    Long deletedId = saveUser("deleted@example.com", "deleted", UserRole.USER);
    saveAccountWithStatus(deletedId, AccountStatus.DELETED);
    saveProgress(deletedId, 99, 9999, 999);

    Long blockedId = saveUser("blocked@example.com", "blocked", UserRole.USER);
    saveAccountWithStatus(blockedId, AccountStatus.BLOCKED);
    saveProgress(blockedId, 99, 9999, 999);

    Long unverifiedId = saveUser("unverified@example.com", "unverified", UserRole.USER);
    saveAccountWithStatus(unverifiedId, AccountStatus.UNVERIFIED);
    saveProgress(unverifiedId, 99, 9999, 999);

    Long adminId = saveUser("admin@example.com", "admin", UserRole.ADMIN_SEED);
    saveActiveAccount(adminId);
    saveProgress(adminId, 99, 9999, 999);

    return expectedIds;
  }

  private Long saveUser(String email, String nickname, UserRole role) {
    UserEntity saved =
        userJpaRepository.save(
            UserEntity.builder()
                .email(email)
                .nickname(nickname)
                .profileImageUrl("https://cdn.example.com/" + nickname + ".png")
                .role(role)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    return saved.getId();
  }

  private void saveActiveAccount(Long userId) {
    saveAccountWithStatus(userId, AccountStatus.ACTIVE);
  }

  private void saveAccountWithStatus(Long userId, AccountStatus status) {
    userAccountJpaRepository.save(
        UserAccountEntity.builder()
            .userId(userId)
            .provider(AuthProvider.LOCAL)
            .status(status)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build());
  }

  private void saveProgress(Long userId, int level, int lifetimeXp, int availableXp) {
    userProgressJpaRepository.save(
        UserProgressEntity.builder()
            .userId(userId)
            .level(level)
            .lifetimeXp(lifetimeXp)
            .availableXp(availableXp)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());
  }
}
