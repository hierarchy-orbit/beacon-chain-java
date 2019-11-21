package org.ethereum.beacon.discovery.enr;

import org.web3j.rlp.RlpString;
import tech.pegasys.artemis.util.bytes.Bytes32;

public interface EnrSchemeInterpreter {
  /** Returns supported scheme */
  EnrScheme getScheme();

  /* Signs nodeRecord, modifying it */
  void sign(NodeRecord nodeRecord, Object signOptions);

  /** Verifies that `nodeRecord` is of scheme implementation */
  default void verify(NodeRecord nodeRecord) {
    if (!nodeRecord.getIdentityScheme().equals(getScheme())) {
      throw new RuntimeException("Interpreter and node record schemes do not match!");
    }
  }

  /** Delivers nodeId according to identity scheme scheme */
  Bytes32 getNodeId(NodeRecord nodeRecord);

  Object decode(String key, RlpString rlpString);

  RlpString encode(String key, Object object);
}