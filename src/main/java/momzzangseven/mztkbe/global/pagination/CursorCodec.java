package momzzangseven.mztkbe.global.pagination;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;

public final class CursorCodec {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private CursorCodec() {}

  public static String encode(KeysetCursor cursor) {
    try {
      String json = OBJECT_MAPPER.writeValueAsString(Payload.from(cursor));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new InvalidCursorException("Invalid cursor", e);
    }
  }

  public static KeysetCursor decode(String encodedCursor, String expectedScope) {
    if (encodedCursor == null || encodedCursor.isBlank()) {
      return null;
    }
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(encodedCursor);
      Payload payload = OBJECT_MAPPER.readValue(decoded, Payload.class);
      KeysetCursor cursor = payload.toCursor();
      if (!cursor.scope().equals(expectedScope)) {
        throw new InvalidCursorException("Cursor scope mismatch");
      }
      return cursor;
    } catch (InvalidCursorException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidCursorException("Invalid cursor", e);
    }
  }

  private record Payload(LocalDateTime createdAt, Long id, String scope) {
    private static Payload from(KeysetCursor cursor) {
      return new Payload(cursor.createdAt(), cursor.id(), cursor.scope());
    }

    private KeysetCursor toCursor() {
      if (id == null) {
        throw new InvalidCursorException("cursor id is required");
      }
      return new KeysetCursor(createdAt, id, scope);
    }
  }
}
