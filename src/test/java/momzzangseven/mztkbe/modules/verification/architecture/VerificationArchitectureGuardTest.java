package momzzangseven.mztkbe.modules.verification.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerificationArchitectureGuardTest {

  private static final Path SRC =
      Path.of("src/main/java/momzzangseven/mztkbe/modules/verification");

  @Test
  @DisplayName("application 계층은 infrastructure 패키지를 직접 참조하지 않는다")
  void applicationMustNotDependOnInfrastructure() throws IOException {
    List<Path> appFiles;
    try (Stream<Path> stream = Files.walk(SRC.resolve("application"))) {
      appFiles = stream.filter(it -> it.toString().endsWith(".java")).toList();
    }

    for (Path file : appFiles) {
      String content = Files.readString(file);
      assertThat(content)
          .as("application references infrastructure: %s", file)
          .doesNotContain("modules.verification.infrastructure");
    }
  }

  @Test
  @DisplayName("api controller는 application port.in만 의존한다")
  void controllerDependsOnInPortOnly() throws IOException {
    List<Path> controllerFiles;
    try (Stream<Path> stream = Files.walk(SRC.resolve("api/controller"))) {
      controllerFiles = stream.filter(it -> it.toString().endsWith(".java")).toList();
    }

    for (Path file : controllerFiles) {
      String content = Files.readString(file);
      assertThat(content).contains("application.port.in");
      assertThat(content).doesNotContain("application.port.out");
      assertThat(content).doesNotContain("infrastructure");
    }
  }

  @Test
  @DisplayName("verification 외부 adapter는 다른 모듈의 infrastructure repository/entity를 직접 참조하지 않는다")
  void externalAdaptersMustNotDependOnOtherModulesInfrastructure() throws IOException {
    List<Path> externalAdapterFiles;
    try (Stream<Path> stream = Files.walk(SRC.resolve("infrastructure/external"))) {
      externalAdapterFiles = stream.filter(it -> it.toString().endsWith(".java")).toList();
    }

    for (Path file : externalAdapterFiles) {
      String content = Files.readString(file);
      assertThat(content).doesNotContain("modules.image.infrastructure");
      assertThat(content).doesNotContain("modules.level.infrastructure");
    }
  }
}
