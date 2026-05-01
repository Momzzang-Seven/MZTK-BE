package momzzangseven.mztkbe.modules.user.api.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import momzzangseven.mztkbe.modules.user.application.dto.GetUserLeaderboardResult;
import momzzangseven.mztkbe.modules.user.application.dto.LeaderboardUserItem;
import momzzangseven.mztkbe.modules.user.application.port.in.GetUserLeaderboardUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("UserLeaderboardController 컨트롤러 계약 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
class UserLeaderboardControllerTest {

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

  @MockitoBean private GetUserLeaderboardUseCase getUserLeaderboardUseCase;

  @Test
  @DisplayName("GET /users/leaderboard 인증 없이 200과 올바른 응답 필드를 반환한다")
  void getUserLeaderboard_publicEndpoint_returns200() throws Exception {
    given(getUserLeaderboardUseCase.execute())
        .willReturn(
            new GetUserLeaderboardResult(
                List.of(
                    new LeaderboardUserItem(1, 100L, "alpha", "https://img", 9, 1200),
                    new LeaderboardUserItem(2, 101L, "beta", null, 8, 1000))));

    mockMvc
        .perform(get("/users/leaderboard"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.users[0].rank").value(1))
        .andExpect(jsonPath("$.data.users[0].userId").value(100))
        .andExpect(jsonPath("$.data.users[0].nickname").value("alpha"))
        .andExpect(jsonPath("$.data.users[0].profileImageUrl").value("https://img"))
        .andExpect(jsonPath("$.data.users[0].level").value(9))
        .andExpect(jsonPath("$.data.users[0].lifetimeXp").value(1200))
        .andExpect(jsonPath("$.data.users[1].rank").value(2));
  }
}
