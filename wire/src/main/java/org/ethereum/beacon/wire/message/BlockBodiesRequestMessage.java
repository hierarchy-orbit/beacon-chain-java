package org.ethereum.beacon.wire.message;

import java.util.List;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;

@SSZSerializable
public class BlockBodiesRequestMessage extends MessagePayload {
  @SSZ private final List<Hash32> blockTreeRoots;

  public BlockBodiesRequestMessage(
      List<Hash32> blockTreeRoots) {
    this.blockTreeRoots = blockTreeRoots;
  }

  public List<Hash32> getBlockTreeRoots() {
    return blockTreeRoots;
  }
}
