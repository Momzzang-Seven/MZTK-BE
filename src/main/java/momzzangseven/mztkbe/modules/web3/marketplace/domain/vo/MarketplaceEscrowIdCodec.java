package momzzangseven.mztkbe.modules.web3.marketplace.domain.vo;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.UUID;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.web3j.utils.Numeric;

/** Codec for marketplace reservation UUIDs and Solidity {@code bytes32} order ids. */
public final class MarketplaceEscrowIdCodec {

  private static final int BYTES32_HEX_LENGTH = 64;
  private static final String ZERO_BYTES32 = "0x" + "0".repeat(BYTES32_HEX_LENGTH);

  private MarketplaceEscrowIdCodec() {}

  public static String orderKey(String orderId) {
    UUID uuid = parseUuid(orderId);
    ByteBuffer buffer = ByteBuffer.allocate(32);
    buffer.putLong(0L);
    buffer.putLong(0L);
    buffer.putLong(uuid.getMostSignificantBits());
    buffer.putLong(uuid.getLeastSignificantBits());
    return Numeric.toHexString(buffer.array()).toLowerCase(Locale.ROOT);
  }

  public static byte[] orderKeyBytes(String canonicalOrderKey) {
    String normalized = normalizeOrderKey(canonicalOrderKey);
    if (ZERO_BYTES32.equals(normalized)) {
      throw new Web3InvalidInputException("orderKey must not be zero bytes32");
    }
    return Numeric.hexStringToByteArray(normalized);
  }

  public static String normalizeOrderKey(String orderKey) {
    if (orderKey == null || orderKey.isBlank()) {
      throw new Web3InvalidInputException("orderKey is required");
    }
    String normalized = orderKey.toLowerCase(Locale.ROOT);
    if (!normalized.startsWith("0x")) {
      normalized = "0x" + normalized;
    }
    if (normalized.length() != 66 || !normalized.substring(2).matches("[0-9a-f]{64}")) {
      throw new Web3InvalidInputException("orderKey must be lowercase 0x + 64 hex chars");
    }
    return normalized;
  }

  public static BigInteger tokenBaseUnits(BigDecimal tokenAmount, int decimals) {
    if (tokenAmount == null || tokenAmount.signum() <= 0) {
      throw new Web3InvalidInputException("tokenAmount must be positive");
    }
    if (decimals < 0 || decimals > 36) {
      throw new Web3InvalidInputException("token decimals must be between 0 and 36");
    }
    return tokenAmount
        .movePointRight(decimals)
        .setScale(0, RoundingMode.UNNECESSARY)
        .toBigIntegerExact();
  }

  private static UUID parseUuid(String orderId) {
    if (orderId == null || orderId.isBlank()) {
      throw new Web3InvalidInputException("orderId is required");
    }
    try {
      return UUID.fromString(orderId);
    } catch (IllegalArgumentException ex) {
      throw new Web3InvalidInputException("orderId must be a UUID");
    }
  }
}
