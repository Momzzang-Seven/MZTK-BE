package momzzangseven.mztkbe.modules.admin.common.application.dto;

public final class AdminPageQueryNormalizer {

  private AdminPageQueryNormalizer() {}

  public static AdminPageQuery normalize(
      Integer page, Integer size, String search, String sort, AdminPagePolicy policy) {
    if (policy == null) {
      throw new IllegalArgumentException("policy is required");
    }

    int normalizedPage = page == null ? policy.defaultPage() : page;
    if (normalizedPage < 0) {
      throw new IllegalArgumentException("page must be zero or positive");
    }

    int normalizedSize = size == null ? policy.defaultSize() : size;
    if (normalizedSize <= 0 || normalizedSize > policy.maxSize()) {
      throw new IllegalArgumentException("size must be between 1 and " + policy.maxSize());
    }

    String normalizedSearch = normalizeSearch(search, policy);
    String normalizedSort = normalizeSort(sort, policy);
    return new AdminPageQuery(normalizedPage, normalizedSize, normalizedSearch, normalizedSort);
  }

  public static String normalizeSearch(String search, AdminPagePolicy policy) {
    if (search == null) {
      return null;
    }
    String trimmed = search.trim();
    if (trimmed.length() > policy.maxSearchLength()) {
      throw new IllegalArgumentException(
          "search must be " + policy.maxSearchLength() + " characters or fewer");
    }
    return trimmed.isBlank() ? null : trimmed;
  }

  public static String normalizeSort(String sort, AdminPagePolicy policy) {
    if (sort == null || sort.isBlank()) {
      return null;
    }
    String normalizedSort = sort.trim();
    if (!policy.sortWhitelist().contains(normalizedSort)) {
      throw new IllegalArgumentException("Unsupported sort value: " + normalizedSort);
    }
    return normalizedSort;
  }
}
