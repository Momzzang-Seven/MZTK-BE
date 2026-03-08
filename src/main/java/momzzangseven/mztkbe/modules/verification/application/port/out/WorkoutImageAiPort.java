package momzzangseven.mztkbe.modules.verification.application.port.out;

import java.math.BigDecimal;
import java.util.List;
import momzzangseven.mztkbe.modules.verification.domain.vo.AppProvider;

/** Outbound port for multimodal AI workout image analysis. */
public interface WorkoutImageAiPort {

  PhotoAnalysisResult analyzeWorkoutPhoto(byte[] imageBytes);

  RecordAnalysisResult analyzeWorkoutRecord(byte[] imageBytes);

  /** AI verdict for workout photo verification. */
  record PhotoAnalysisResult(
      boolean workoutPhoto, BigDecimal confidenceScore, String modelName, List<String> reasons) {}

  /** AI verdict for workout record verification. */
  record RecordAnalysisResult(
      boolean workoutRecord,
      boolean dateVisible,
      String exerciseDate,
      AppProvider appProvider,
      BigDecimal confidenceScore,
      String modelName,
      List<String> reasons) {}
}
