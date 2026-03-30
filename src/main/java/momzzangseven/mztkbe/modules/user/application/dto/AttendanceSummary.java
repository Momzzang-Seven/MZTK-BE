package momzzangseven.mztkbe.modules.user.application.dto;

/**
 * Carries today's attendance status and weekly attendance count for a user as returned by {@code
 * LoadAttendanceSummaryPort}. Used internally by {@code GetMyProfileService}.
 */
public record AttendanceSummary(boolean hasAttendedToday, int weeklyAttendanceCount) {}
