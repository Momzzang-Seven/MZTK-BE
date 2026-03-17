package momzzangseven.mztkbe.modules.verification.application.dto;

import java.nio.file.Path;

public final class PreparedAnalysisImage implements AutoCloseable {

  private final Path path;
  private final Runnable cleanup;
  private boolean closed;

  public PreparedAnalysisImage(Path path, Runnable cleanup) {
    this.path = path;
    this.cleanup = cleanup;
  }

  public static PreparedAnalysisImage noop(Path path) {
    return new PreparedAnalysisImage(path, () -> {});
  }

  public Path path() {
    return path;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    cleanup.run();
  }
}
