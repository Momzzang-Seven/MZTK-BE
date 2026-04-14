package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SponsorDailyUsage {

  private final Long id;
  private final Long userId;
  private final LocalDate usageDateKst;
  private final BigInteger reservedCostWei;
  private final BigInteger consumedCostWei;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  public static SponsorDailyUsage create(Long userId, LocalDate usageDateKst) {
    if (userId == null || userId <= 0) {
      throw new Web3InvalidInputException("userId must be positive");
    }
    if (usageDateKst == null) {
      throw new Web3InvalidInputException("usageDateKst is required");
    }
    return SponsorDailyUsage.builder()
        .userId(userId)
        .usageDateKst(usageDateKst)
        .reservedCostWei(BigInteger.ZERO)
        .consumedCostWei(BigInteger.ZERO)
        .build();
  }

  public SponsorDailyUsage reserve(BigInteger deltaWei) {
    validateNonNegative(deltaWei, "reserve amount");
    return toBuilder().reservedCostWei(nullSafe(reservedCostWei).add(deltaWei)).build();
  }

  public SponsorDailyUsage release(BigInteger deltaWei) {
    validateNonNegative(deltaWei, "release amount");
    BigInteger next = nullSafe(reservedCostWei).subtract(deltaWei);
    if (next.signum() < 0) {
      throw new Web3InvalidInputException("reservedCostWei cannot become negative");
    }
    return toBuilder().reservedCostWei(next).build();
  }

  public SponsorDailyUsage consume(BigInteger deltaWei) {
    validateNonNegative(deltaWei, "consume amount");
    return toBuilder().consumedCostWei(nullSafe(consumedCostWei).add(deltaWei)).build();
  }

  public BigInteger totalExposureWei() {
    return nullSafe(reservedCostWei).add(nullSafe(consumedCostWei));
  }

  private static void validateNonNegative(BigInteger deltaWei, String field) {
    if (deltaWei == null || deltaWei.signum() < 0) {
      throw new Web3InvalidInputException(field + " must be >= 0");
    }
  }

  private static BigInteger nullSafe(BigInteger value) {
    return value == null ? BigInteger.ZERO : value;
  }
}
