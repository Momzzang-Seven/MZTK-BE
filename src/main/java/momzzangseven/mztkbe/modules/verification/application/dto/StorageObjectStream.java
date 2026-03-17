package momzzangseven.mztkbe.modules.verification.application.dto;

import java.io.IOException;
import java.io.InputStream;

public record StorageObjectStream(InputStream stream, long contentLength, String contentType)
    implements AutoCloseable {

  @Override
  public void close() throws IOException {
    stream.close();
  }
}
