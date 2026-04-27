package momzzangseven.mztkbe.global.persistence;

public final class LikePatternEscaper {

  private LikePatternEscaper() {}

  public static String escape(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }
}
