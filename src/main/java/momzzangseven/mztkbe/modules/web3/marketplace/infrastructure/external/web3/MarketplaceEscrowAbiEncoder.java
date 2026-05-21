package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceAdminEscrowCallDataPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceEscrowCallDataPort;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;

@Component
public class MarketplaceEscrowAbiEncoder
    implements BuildMarketplaceEscrowCallDataPort, BuildMarketplaceAdminEscrowCallDataPort {

  @Override
  public String encode(
      MarketplaceExecutionActionType actionType,
      String orderKey,
      String tokenAddress,
      String trainerAddress,
      BigInteger priceBaseUnits,
      Long signedAt,
      byte[] signatureBytes) {
    if (actionType == null) {
      throw new Web3InvalidInputException("actionType is required");
    }
    if (!actionType.isUserAction()) {
      throw new Web3InvalidInputException("admin marketplace action is not supported here");
    }
    Function function =
        switch (actionType) {
          case MARKETPLACE_CLASS_PURCHASE ->
              new Function(
                  "purchaseClass",
                  List.of(
                      bytes32(orderKey),
                      new Address(tokenAddress),
                      new Address(trainerAddress),
                      uint256(priceBaseUnits),
                      uint256(signedAt),
                      signature(signatureBytes)),
                  Collections.emptyList());
          case MARKETPLACE_CLASS_CANCEL ->
              new Function(
                  "cancelClass",
                  List.of(bytes32(orderKey), uint256(signedAt), signature(signatureBytes)),
                  Collections.emptyList());
          case MARKETPLACE_CLASS_CONFIRM ->
              new Function(
                  "confirmClass",
                  List.of(bytes32(orderKey), uint256(signedAt), signature(signatureBytes)),
                  Collections.emptyList());
          case MARKETPLACE_CLASS_EXPIRED_REFUND ->
              new Function(
                  "claimExpiredRefund", List.of(bytes32(orderKey)), Collections.emptyList());
          case MARKETPLACE_ADMIN_REFUND, MARKETPLACE_ADMIN_SETTLE ->
              throw new Web3InvalidInputException("admin marketplace action is not supported here");
        };
    return FunctionEncoder.encode(function);
  }

  @Override
  public String encodeAdminRefund(String orderKey) {
    return FunctionEncoder.encode(
        new Function("adminRefund", List.of(bytes32(orderKey)), Collections.emptyList()));
  }

  @Override
  public String encodeAdminSettle(String orderKey) {
    return FunctionEncoder.encode(
        new Function("adminSettle", List.of(bytes32(orderKey)), Collections.emptyList()));
  }

  private Type<?> bytes32(String orderKey) {
    return new Bytes32(MarketplaceEscrowIdCodec.orderKeyBytes(orderKey));
  }

  private Uint256 uint256(BigInteger value) {
    if (value == null || value.signum() < 0) {
      throw new Web3InvalidInputException("uint256 value must be >= 0");
    }
    return new Uint256(value);
  }

  private Uint256 uint256(Long value) {
    if (value == null || value < 0) {
      throw new Web3InvalidInputException("signedAt must be non-negative");
    }
    return new Uint256(BigInteger.valueOf(value));
  }

  private DynamicBytes signature(byte[] signatureBytes) {
    if (signatureBytes == null || signatureBytes.length != 65) {
      throw new Web3InvalidInputException("signature must be 65 bytes");
    }
    return new DynamicBytes(signatureBytes.clone());
  }
}
