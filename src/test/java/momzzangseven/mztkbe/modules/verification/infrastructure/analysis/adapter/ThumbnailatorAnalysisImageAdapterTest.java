package momzzangseven.mztkbe.modules.verification.infrastructure.analysis.adapter;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.matrixlab.webp4j.WebPCodec;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import momzzangseven.mztkbe.modules.verification.application.port.out.ImageCodecSupportPort;
import momzzangseven.mztkbe.modules.verification.application.service.VerificationImagePolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ThumbnailatorAnalysisImageAdapterTest {

  private final Logger logger =
      (Logger) LoggerFactory.getLogger(ThumbnailatorAnalysisImageAdapter.class);
  private final List<Path> tempDirs = new ArrayList<>();
  private ListAppender<ILoggingEvent> logAppender;

  @AfterEach
  void tearDown() throws IOException {
    if (logAppender != null) {
      logger.detachAppender(logAppender);
    }

    for (Path tempDir : tempDirs) {
      if (Files.notExists(tempDir)) {
        continue;
      }
      try (Stream<Path> stream = Files.walk(tempDir)) {
        stream.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
      }
    }
  }

  @Test
  void preparesStrictWebpNextToOriginalPath() throws Exception {
    ThumbnailatorAnalysisImageAdapter adapter =
        new ThumbnailatorAnalysisImageAdapter(imagePolicy(5242880L, 25000000L), codecSupport(true));
    Path originalPath = writeTempImage("original.png", createPngBytes(2048, 1024));

    Path analysisPath;
    try (var prepared = adapter.prepare(originalPath, 1024, 0.8d)) {
      analysisPath = prepared.path();
      assertThat(Files.exists(analysisPath)).isTrue();
      assertThat(Files.exists(originalPath)).isTrue();
      assertThat(analysisPath.getFileName().toString()).isEqualTo("analysis.webp");
      assertThat(analysisPath.getParent()).isEqualTo(originalPath.getParent());
      assertThat(new String(Files.readAllBytes(analysisPath), 0, 4, ISO_8859_1)).isEqualTo("RIFF");
      assertThat(new String(Files.readAllBytes(analysisPath), 8, 4, ISO_8859_1)).isEqualTo("WEBP");
      BufferedImage rendered = WebPCodec.decodeImage(Files.readAllBytes(analysisPath));
      assertThat(Math.max(rendered.getWidth(), rendered.getHeight())).isEqualTo(1024);
    }

    assertThat(Files.exists(analysisPath)).isFalse();
    assertThat(Files.exists(originalPath)).isTrue();
  }

  @Test
  void rejectsUndecodableSourceImage() throws Exception {
    ThumbnailatorAnalysisImageAdapter adapter =
        new ThumbnailatorAnalysisImageAdapter(imagePolicy(5242880L, 25000000L), codecSupport(true));
    Path originalPath = writeTempImage("original.jpg", new byte[] {1, 2, 3});

    assertThatThrownBy(() -> adapter.prepare(originalPath, 1024, 0.8d))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("decode");
  }

  @Test
  void rejectsOversizedPixelCount() throws Exception {
    ThumbnailatorAnalysisImageAdapter adapter =
        new ThumbnailatorAnalysisImageAdapter(imagePolicy(5242880L, 10L), codecSupport(true));
    Path originalPath = writeTempImage("original.png", createPngBytes(100, 100));

    assertThatThrownBy(() -> adapter.prepare(originalPath, 1024, 0.8d))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("pixel");
  }

  @Test
  void rejectsHeifWhenRuntimeDecoderIsUnavailable() throws Exception {
    ThumbnailatorAnalysisImageAdapter adapter =
        new ThumbnailatorAnalysisImageAdapter(
            imagePolicy(5242880L, 25000000L), codecSupport(false));
    Path originalPath = writeTempImage("original.heic", new byte[] {1, 2, 3});

    assertThatThrownBy(() -> adapter.prepare(originalPath, 1024, 0.8d))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("HEIF");
  }

  @Test
  void logsWarningAndContinuesCleanupWhenDeleteFails() throws Exception {
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);

    RecordingDeleteAdapter adapter =
        new RecordingDeleteAdapter(imagePolicy(5242880L, 25000000L), codecSupport(true));
    Path originalPath = writeTempImage("original.jpg", new byte[] {1, 2, 3});

    assertThatThrownBy(() -> adapter.prepare(originalPath, 1024, 0.8d))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("decode");

    assertThat(adapter.deletedNames()).containsExactly("analysis.webp");
    assertThat(
            logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("Failed to delete verification temp path")))
        .isTrue();
  }

  private VerificationImagePolicy imagePolicy(long maxOriginalBytes, long maxPixels) {
    return new VerificationImagePolicy(
        new VerificationRuntimeProperties(
            new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, false),
            new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
            new VerificationRuntimeProperties.Image(maxOriginalBytes, maxPixels)));
  }

  private ImageCodecSupportPort codecSupport(boolean heifDecodeAvailable) {
    return new ImageCodecSupportPort() {
      @Override
      public boolean isHeifDecodeAvailable() {
        return heifDecodeAvailable;
      }

      @Override
      public boolean isWebpWriteAvailable() {
        return true;
      }
    };
  }

  private byte[] createPngBytes(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  private Path writeTempImage(String filename, byte[] bytes) throws IOException {
    Path tempDir = Files.createTempDirectory("verification-analysis-test-");
    tempDirs.add(tempDir);
    Path imagePath = tempDir.resolve(filename);
    Files.write(imagePath, bytes);
    return imagePath;
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // best-effort cleanup for temp test files
    }
  }

  private static final class RecordingDeleteAdapter extends ThumbnailatorAnalysisImageAdapter {

    private final List<String> deletedNames = new ArrayList<>();

    private RecordingDeleteAdapter(
        VerificationImagePolicy verificationImagePolicy,
        ImageCodecSupportPort imageCodecSupportPort) {
      super(verificationImagePolicy, imageCodecSupportPort);
    }

    @Override
    protected void deletePathIfExists(Path path) throws IOException {
      deletedNames.add(path.getFileName().toString());
      throw new IOException("boom");
    }

    private List<String> deletedNames() {
      return deletedNames;
    }
  }
}
