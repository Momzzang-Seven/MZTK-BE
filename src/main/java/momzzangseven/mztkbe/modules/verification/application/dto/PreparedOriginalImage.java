package momzzangseven.mztkbe.modules.verification.application.dto;

import java.nio.file.Path;

public final class PreparedOriginalImage implements AutoCloseable {

  private final Path path;
  private final Runnable cleanup;
  private boolean closed;

  public PreparedOriginalImage(Path path, Runnable cleanup) {
    this.path = path;
    this.cleanup = cleanup;
  }

  public static PreparedOriginalImage noop(Path path) {
    return new PreparedOriginalImage(path, () -> {});
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
