package org.ethereum.beacon.wire.message;

import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.uint.UInt64;

@SSZSerializable
public class HelloMessage extends MessagePayload {
  @SSZ private final byte networkId;
  @SSZ private final UInt64 chainId;
  @SSZ private final Hash32 latestFinalizedRoot;
  @SSZ private final EpochNumber latestFinalizedEpoch;
  @SSZ private final Hash32 bestRoot;
  @SSZ private final SlotNumber bestSlot;

  public HelloMessage(byte networkId, UInt64 chainId,
      Hash32 latestFinalizedRoot, EpochNumber latestFinalizedEpoch,
      Hash32 bestRoot, SlotNumber bestSlot) {
    this.networkId = networkId;
    this.chainId = chainId;
    this.latestFinalizedRoot = latestFinalizedRoot;
    this.latestFinalizedEpoch = latestFinalizedEpoch;
    this.bestRoot = bestRoot;
    this.bestSlot = bestSlot;
  }

  public byte getNetworkId() {
    return networkId;
  }

  public UInt64 getChainId() {
    return chainId;
  }

  public Hash32 getLatestFinalizedRoot() {
    return latestFinalizedRoot;
  }

  public EpochNumber getLatestFinalizedEpoch() {
    return latestFinalizedEpoch;
  }

  public Hash32 getBestRoot() {
    return bestRoot;
  }

  public SlotNumber getBestSlot() {
    return bestSlot;
  }
}
