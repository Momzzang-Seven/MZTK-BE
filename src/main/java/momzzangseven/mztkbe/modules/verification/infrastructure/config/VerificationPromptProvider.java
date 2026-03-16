package momzzangseven.mztkbe.modules.verification.infrastructure.config;

import java.nio.charset.StandardCharsets;
import lombok.Getter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
@Getter
public class VerificationPromptProvider {

  private final String workoutPhotoSystemInstruction;
  private final String workoutPhotoUserPrompt;
  private final String workoutPhotoResponseSchema;
  private final String workoutRecordSystemInstruction;
  private final String workoutRecordUserPrompt;
  private final String workoutRecordResponseSchema;

  public VerificationPromptProvider() {
    this.workoutPhotoSystemInstruction =
        read("prompts/verification/workout-photo-system-instruction.txt");
    this.workoutPhotoUserPrompt = read("prompts/verification/workout-photo-user-prompt.txt");
    this.workoutPhotoResponseSchema =
        read("prompts/verification/workout-photo-response-schema.json");
    this.workoutRecordSystemInstruction =
        read("prompts/verification/workout-record-system-instruction.txt");
    this.workoutRecordUserPrompt = read("prompts/verification/workout-record-user-prompt.txt");
    this.workoutRecordResponseSchema =
        read("prompts/verification/workout-record-response-schema.json");
  }

  private String read(String path) {
    try {
      return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to load prompt resource: " + path, ex);
    }
  }
}
