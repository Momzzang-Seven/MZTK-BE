package momzzangseven.mztkbe.modules.verification.infrastructure.ai.client;

import java.nio.file.Path;
import momzzangseven.mztkbe.modules.verification.application.exception.AiUnavailableException;

public class UnavailableVerificationAiJsonClient implements VerificationAiJsonClient {

  @Override
  public String analyzeWorkoutPhoto(
      Path analysisImagePath,
      String systemInstruction,
      String userPrompt,
      String responseSchemaJson) {
    throw new AiUnavailableException("Workout photo AI client is not configured");
  }

  @Override
  public String analyzeWorkoutRecord(
      Path analysisImagePath,
      String systemInstruction,
      String userPrompt,
      String responseSchemaJson) {
    throw new AiUnavailableException("Workout record AI client is not configured");
  }
}
