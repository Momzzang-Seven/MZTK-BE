package momzzangseven.mztkbe.modules.user.infrastructure.external.level.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;
import momzzangseven.mztkbe.modules.level.application.dto.GetAttendanceStatusResult;
import momzzangseven.mztkbe.modules.level.application.dto.GetWeeklyAttendanceResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GetAttendanceStatusUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GetWeeklyAttendanceUseCase;
import momzzangseven.mztkbe.modules.user.application.dto.AttendanceSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AttendanceSummaryAdapter 단위 테스트")
class AttendanceSummaryAdapterTest {

  @Mock private GetAttendanceStatusUseCase getAttendanceStatusUseCase;
  @Mock private GetWeeklyAttendanceUseCase getWeeklyAttendanceUseCase;

  @InjectMocks private AttendanceSummaryAdapter adapter;

  @Test
  @DisplayName("[M-10] AttendanceSummaryAdapter가 두 use case를 조합해 AttendanceSummary를 반환한다")
  void loadSummary_combinesTwoUseCaseResultsIntoAttendanceSummary() {
    // given
    LocalDate today = LocalDate.now();
    given(getAttendanceStatusUseCase.execute(1L))
        .willReturn(GetAttendanceStatusResult.of(today, true, 3));
    given(getWeeklyAttendanceUseCase.execute(1L))
        .willReturn(
            GetWeeklyAttendanceResult.of(
                today.minusDays(6),
                today,
                List.of(
                    today,
                    today.minusDays(1),
                    today.minusDays(2),
                    today.minusDays(3),
                    today.minusDays(4))));

    // when
    AttendanceSummary summary = adapter.loadSummary(1L);

    // then
    assertThat(summary.hasAttendedToday()).isTrue();
    assertThat(summary.weeklyAttendanceCount()).isEqualTo(5);
  }
}
