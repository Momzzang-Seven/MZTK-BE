package momzzangseven.mztkbe.modules.marketplace.sanction.application.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import momzzangseven.mztkbe.modules.marketplace.sanction.application.dto.RecordTrainerStrikeCommand;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.out.ManageTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.out.ManageTrainerSanctionPort.RecordStrikeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecordTrainerStrikeService 단위 테스트")
class RecordTrainerStrikeServiceTest {

  @Mock private ManageTrainerSanctionPort manageTrainerSanctionPort;

  @InjectMocks private RecordTrainerStrikeService sut;

  private static final Long TRAINER_ID = 7L;

  @Nested
  @DisplayName("execute() — 성공 케이스")
  class 성공 {

    @Test
    @DisplayName("[RS-01] TIMEOUT 이유로 스트라이크 기록 시 ManageTrainerSanctionPort 정확히 1회 호출")
    void 타임아웃_스트라이크_기록() {
      // given
      given(
              manageTrainerSanctionPort.recordStrike(
                  eq(TRAINER_ID), eq(RecordTrainerStrikeCommand.REASON_TIMEOUT)))
          .willReturn(new RecordStrikeResult(1, false));

      // when
      sut.execute(
          new RecordTrainerStrikeCommand(TRAINER_ID, RecordTrainerStrikeCommand.REASON_TIMEOUT));

      // then
      then(manageTrainerSanctionPort)
          .should()
          .recordStrike(TRAINER_ID, RecordTrainerStrikeCommand.REASON_TIMEOUT);
    }

    @Test
    @DisplayName("[RS-02] REJECT 이유로 스트라이크 기록 시 ManageTrainerSanctionPort 정확히 1회 호출")
    void 거절_스트라이크_기록() {
      // given
      given(
              manageTrainerSanctionPort.recordStrike(
                  eq(TRAINER_ID), eq(RecordTrainerStrikeCommand.REASON_REJECT)))
          .willReturn(new RecordStrikeResult(2, false));

      // when
      sut.execute(
          new RecordTrainerStrikeCommand(TRAINER_ID, RecordTrainerStrikeCommand.REASON_REJECT));

      // then
      then(manageTrainerSanctionPort)
          .should()
          .recordStrike(TRAINER_ID, RecordTrainerStrikeCommand.REASON_REJECT);
    }

    @Test
    @DisplayName("[RS-03] 스트라이크 기록 후 isBanned=true 반환 시 예외 없이 정상 처리 (로그만 기록)")
    void 밴_상태_반환_정상처리() {
      // given
      given(manageTrainerSanctionPort.recordStrike(eq(TRAINER_ID), eq(RecordTrainerStrikeCommand.REASON_TIMEOUT)))
          .willReturn(new RecordStrikeResult(3, true));

      // when: 예외 없이 완료되어야 함
      sut.execute(
          new RecordTrainerStrikeCommand(TRAINER_ID, RecordTrainerStrikeCommand.REASON_TIMEOUT));

      // then
      then(manageTrainerSanctionPort)
          .should()
          .recordStrike(TRAINER_ID, RecordTrainerStrikeCommand.REASON_TIMEOUT);
    }
  }
}
