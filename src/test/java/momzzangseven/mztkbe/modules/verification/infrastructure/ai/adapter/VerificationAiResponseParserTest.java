package momzzangseven.mztkbe.modules.verification.infrastructure.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import momzzangseven.mztkbe.modules.verification.application.exception.AiMalformedResponseException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiResponseSchemaInvalidException;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerificationAiResponseParserTest {

  private VerificationAiResponseParser parser;

  @BeforeEach
  void setUp() {
    parser = new VerificationAiResponseParser(new ObjectMapper());
  }

  @Test
  void parsesWorkoutPhotoApproved() {
    WorkoutPhotoAiResponse result =
        parser.parseWorkoutPhoto(
            """
            {"workoutPhoto":true,"rejectionReasonCode":null,"confidenceScore":0.95}
            """);

    assertThat(result.workoutPhoto()).isTrue();
    assertThat(result.rejectionReasonCode()).isNull();
    assertThat(result.confidenceScore()).isEqualTo(0.95d);
  }

  @Test
  void rejectsWorkoutPhotoWithoutReasonWhenNotApproved() {
    assertThatThrownBy(
            () ->
                parser.parseWorkoutPhoto(
                    """
                    {"workoutPhoto":false,"rejectionReasonCode":null,"confidenceScore":0.12}
                    """))
        .isInstanceOf(AiResponseSchemaInvalidException.class)
        .hasMessageContaining("workoutPhoto=false requires rejectionReasonCode");
  }

  @Test
  void rejectsWorkoutPhotoWhenReasonCodeIsUnsupported() {
    assertThatThrownBy(
            () ->
                parser.parseWorkoutPhoto(
                    """
                    {"workoutPhoto":false,"rejectionReasonCode":"DATE_NOT_VISIBLE","confidenceScore":0.12}
                    """))
        .isInstanceOf(AiResponseSchemaInvalidException.class)
        .hasMessageContaining("unsupported code");
  }

  @Test
  void rejectsWorkoutPhotoWhenScoreIsOutOfRange() {
    assertThatThrownBy(
            () ->
                parser.parseWorkoutPhoto(
                    """
                    {"workoutPhoto":true,"rejectionReasonCode":null,"confidenceScore":1.2}
                    """))
        .isInstanceOf(AiResponseSchemaInvalidException.class)
        .hasMessageContaining("between 0 and 1");
  }

  @Test
  void throwsMalformedResponseForInvalidJson() {
    assertThatThrownBy(() -> parser.parseWorkoutPhoto("{not-json"))
        .isInstanceOf(AiMalformedResponseException.class);
  }

  @Test
  void parsesWorkoutRecordApproved() {
    WorkoutRecordAiResponse result =
        parser.parseWorkoutRecord(
            """
            {"workoutRecord":true,"rejectionReasonCode":null,"dateVisible":true,"exerciseDate":"2026-03-13","confidenceScore":0.98}
            """);

    assertThat(result.workoutRecord()).isTrue();
    assertThat(result.rejectionReasonCode()).isNull();
    assertThat(result.dateVisible()).isTrue();
    assertThat(result.exerciseDate()).isEqualTo(LocalDate.of(2026, 3, 13));
    assertThat(result.confidenceScore()).isEqualTo(0.98d);
  }

  @Test
  void rejectsWorkoutRecordWhenApprovedButReasonIsPresent() {
    assertThatThrownBy(
            () ->
                parser.parseWorkoutRecord(
                    """
                    {"workoutRecord":true,"rejectionReasonCode":"LOW_CONFIDENCE","dateVisible":true,"exerciseDate":"2026-03-13","confidenceScore":0.98}
                    """))
        .isInstanceOf(AiResponseSchemaInvalidException.class)
        .hasMessageContaining("rejectionReasonCode=null");
  }

  @Test
  void rejectsWorkoutRecordWhenApprovedButDateVisibleIsFalse() {
    assertThatThrownBy(
            () ->
                parser.parseWorkoutRecord(
                    """
                    {"workoutRecord":true,"rejectionReasonCode":null,"dateVisible":false,"exerciseDate":"2026-03-13","confidenceScore":0.98}
                    """))
        .isInstanceOf(AiResponseSchemaInvalidException.class)
        .hasMessageContaining("dateVisible=true");
  }

  @Test
  void rejectsWorkoutRecordWhenApprovedButExerciseDateIsNull() {
    assertThatThrownBy(
            () ->
                parser.parseWorkoutRecord(
                    """
                    {"workoutRecord":true,"rejectionReasonCode":null,"dateVisible":true,"exerciseDate":null,"confidenceScore":0.98}
                    """))
        .isInstanceOf(AiResponseSchemaInvalidException.class)
        .hasMessageContaining("requires exerciseDate");
  }

  @Test
  void rejectsWorkoutRecordWhenNotApprovedWithoutReason() {
    assertThatThrownBy(
            () ->
                parser.parseWorkoutRecord(
                    """
                    {"workoutRecord":false,"rejectionReasonCode":null,"dateVisible":false,"exerciseDate":null,"confidenceScore":0.10}
                    """))
        .isInstanceOf(AiResponseSchemaInvalidException.class)
        .hasMessageContaining("requires rejectionReasonCode");
  }

  @Test
  void rejectsWorkoutRecordWhenDateFormatIsInvalid() {
    assertThatThrownBy(
            () ->
                parser.parseWorkoutRecord(
                    """
                    {"workoutRecord":false,"rejectionReasonCode":"DATE_NOT_VISIBLE","dateVisible":false,"exerciseDate":"2026/03/13","confidenceScore":0.10}
                    """))
        .isInstanceOf(AiResponseSchemaInvalidException.class)
        .hasMessageContaining("YYYY-MM-DD");
  }

  @Test
  void parsesWorkoutRecordRejectedPath() {
    WorkoutRecordAiResponse result =
        parser.parseWorkoutRecord(
            """
            {"workoutRecord":false,"rejectionReasonCode":"DATE_NOT_VISIBLE","dateVisible":false,"exerciseDate":null,"confidenceScore":0.21}
            """);

    assertThat(result.workoutRecord()).isFalse();
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.DATE_NOT_VISIBLE);
    assertThat(result.dateVisible()).isFalse();
    assertThat(result.exerciseDate()).isNull();
    assertThat(result.confidenceScore()).isEqualTo(0.21d);
  }
}
