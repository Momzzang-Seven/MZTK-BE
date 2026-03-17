package momzzangseven.mztkbe.modules.verification.infrastructure.ai.client;

import java.nio.file.Path;

public interface VerificationAiJsonClient {

  String analyzeWorkoutPhoto(
      Path analysisImagePath,
      String systemInstruction,
      String userPrompt,
      String responseSchemaJson);

  String analyzeWorkoutRecord(
      Path analysisImagePath,
      String systemInstruction,
      String userPrompt,
      String responseSchemaJson);
}
