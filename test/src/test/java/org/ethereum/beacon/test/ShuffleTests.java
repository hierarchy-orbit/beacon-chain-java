package org.ethereum.beacon.test;

import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.test.runner.shuffle.ShuffleRunner;
import org.ethereum.beacon.test.type.shuffle.ShuffleTestCase;
import org.junit.Test;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.uint.UInt64;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Committee shuffle test */
public class ShuffleTests extends TestUtils {
  private Path SUBDIR = Paths.get("phase0", "shuffling");

  @Test
  public void testShuffling() {
    runSpecTestsInResourceDirs(
        MINIMAL_TESTS,
        MAINNET_TESTS,
        SUBDIR,
        ShuffleTestCase.class,
        input -> {
          ShuffleRunner testRunner =
              new ShuffleRunner(
                  input.getValue0(),
                  input.getValue1(),
                  objects ->
                      input
                          .getValue1()
                          .compute_committee(
                              objects.getValue0(),
                              UInt64.ZERO,
                              UInt64.valueOf(objects.getValue2()),
                              objects.getValue1()));
          return testRunner.run();
        });
  }

  /**
   * Runs tests on optimized version of get_shuffling, like in {@link
   * BeaconChainSpec#compute_committee2(List, UInt64, UInt64, Bytes32)}
   */
  @Test
  public void testShuffling2() {
    runSpecTestsInResourceDirs(
        MINIMAL_TESTS,
        MAINNET_TESTS,
        SUBDIR,
        ShuffleTestCase.class,
        input -> {
          ShuffleRunner testRunner =
              new ShuffleRunner(
                  input.getValue0(),
                  input.getValue1(),
                  objects ->
                      input
                          .getValue1()
                          .compute_committee2(
                              objects.getValue0(),
                              UInt64.ZERO,
                              UInt64.valueOf(objects.getValue2()),
                              objects.getValue1()));
          return testRunner.run();
        });
  }
}
