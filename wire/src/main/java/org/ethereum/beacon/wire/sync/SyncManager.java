package org.ethereum.beacon.wire.sync;

import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.envelops.SignedBeaconBlock;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.wire.Feedback;
import org.ethereum.beacon.wire.WireApiSync;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;

// TODO: revisit and complete this interface
public interface SyncManager {

  Disposable subscribeToOnlineBlocks(Publisher<Feedback<SignedBeaconBlock>> onlineBlocks);

  Disposable subscribeToFinalizedBlocks(Publisher<SignedBeaconBlock> finalBlocks);

  void setSyncApi(WireApiSync syncApi);

  Publisher<Feedback<SignedBeaconBlock>> getBlocksReadyToImport();

  void start();

  void stop();

  Publisher<SyncMode> getSyncModeStream();

  Publisher<Boolean> getIsSyncingStream();

  Publisher<SlotNumber> getStartSlotStream();

  Publisher<SlotNumber> getLastSlotStream();

  enum SyncMode {
    Long,
    Short
  }
}
