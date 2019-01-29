package org.ethereum.beacon.validator;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.ethereum.beacon.chain.observer.ObservableBeaconState;
import org.ethereum.beacon.consensus.SpecHelpers;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.validator.crypto.MessageSigner;
import tech.pegasys.artemis.util.bytes.Bytes96;
import tech.pegasys.artemis.util.uint.UInt24;
import tech.pegasys.artemis.util.uint.UInt64;

/** Runs a single validator in the same instance with chain processing. */
public class BeaconChainValidator implements ValidatorService {

  private ValidatorCredentials credentials;
  private BeaconChainProposer proposer;
  private BeaconChainAttester attester;
  private SpecHelpers specHelpers;
  private MessageSigner<Bytes96> messageSigner;

  private ScheduledExecutorService executor;

  private UInt24 index = UInt24.MAX_VALUE;
  private UInt64 lastProcessedSlot = UInt64.MAX_VALUE;

  private ObservableBeaconState recentState;

  public BeaconChainValidator(
      ValidatorCredentials credentials,
      BeaconChainProposer proposer,
      BeaconChainAttester attester,
      SpecHelpers specHelpers,
      MessageSigner<Bytes96> messageSigner) {
    this.credentials = credentials;
    this.proposer = proposer;
    this.attester = attester;
    this.specHelpers = specHelpers;
    this.messageSigner = messageSigner;
  }

  @Override
  public void start() {
    this.executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread t = new Thread(runnable, "validator-service");
              t.setDaemon(true);
              return t;
            });
    subscribeToSlotStateUpdates(this::processSlotState);
    subscribeToObservableStateUpdates(this::keepRecentState);
  }

  @Override
  public void stop() {
    this.executor.shutdown();
  }

  private void init(BeaconState state) {
    this.index = specHelpers.get_validator_index_by_pubkey(state, credentials.getBlsPublicKey());
    setSlotProcessed(state);
  }

  private void keepRecentState(ObservableBeaconState state) {
    this.recentState = state;
  }

  private void processSlotState(ObservableBeaconState observableState) {
    keepRecentState(observableState);
    BeaconState state = observableState.getLatestSlotState();

    if (!isInitialized() && isCurrentSlot(state)) {
      init(state);
    }

    if (isInitialized() && !isSlotProcessed(state)) {
      setSlotProcessed(state);
      runTasks(observableState);
    }
  }

  private void runTasks(final ObservableBeaconState observableState) {
    BeaconState state = observableState.getLatestSlotState();

    final List<UInt24> firstCommittee =
        specHelpers.get_shard_committees_at_slot(state, state.getSlot()).get(0).getCommittee();

    // trigger proposer
    UInt24 proposerIndex =
        specHelpers.get_beacon_proposer_index_in_committee(firstCommittee, state.getSlot());
    if (index.equals(proposerIndex)) {
      runAsync(() -> propose(observableState));
    }

    // trigger attester at a halfway through the slot
    if (index.equals(proposerIndex) || Collections.binarySearch(firstCommittee, index) >= 0) {
      UInt64 startAt = specHelpers.get_slot_middle_time(state, state.getSlot());
      schedule(startAt, () -> attest(this.index, firstCommittee, this.recentState));
    }
  }

  private void runAsync(Runnable routine) {
    executor.execute(routine);
  }

  private void schedule(UInt64 startAt, Runnable routine) {
    long startAtMillis = startAt.getValue() * 1000;
    assert System.currentTimeMillis() < startAtMillis;
    executor.schedule(routine, System.currentTimeMillis() - startAtMillis, TimeUnit.MILLISECONDS);
  }

  private void propose(final ObservableBeaconState observableState) {
    BeaconBlock newBlock = proposer.propose(index, observableState, messageSigner);
    propagateBlock(newBlock);
  }

  private void attest(
      final UInt24 index,
      final List<UInt24> committee,
      final ObservableBeaconState observableState) {
    Attestation attestation =
        attester.attest(
            index,
            committee,
            specHelpers.getChainSpec().getBeaconChainShardNumber(),
            observableState,
            messageSigner);
    propagateAttestation(attestation);
  }

  private void setSlotProcessed(BeaconState state) {
    this.lastProcessedSlot = state.getSlot();
  }

  private boolean isSlotProcessed(BeaconState state) {
    return lastProcessedSlot.compareTo(state.getSlot()) < 0;
  }

  private boolean isCurrentSlot(BeaconState state) {
    return specHelpers.is_current_slot(state);
  }

  private boolean isInitialized() {
    return index.compareTo(UInt24.MAX_VALUE) < 0;
  }

  /* FIXME: stub for streams. */
  private void propagateBlock(BeaconBlock newBlock) {}

  private void propagateAttestation(Attestation attestation) {}

  private void subscribeToSlotStateUpdates(Consumer<ObservableBeaconState> payload) {}

  private void subscribeToObservableStateUpdates(Consumer<ObservableBeaconState> payload) {}
}
