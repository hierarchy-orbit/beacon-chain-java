package org.ethereum.beacon.core.operations;

import com.google.common.base.Objects;
import org.ethereum.beacon.core.BeaconBlockBody;
import org.ethereum.beacon.core.operations.attestation.AttestationData;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.collections.Bitlist;

import javax.annotation.Nullable;
import java.util.stream.Collectors;

import static tech.pegasys.artemis.util.collections.ReadList.VARIABLE_SIZE;

/**
 * Attests on a block linked to particular slot in particular shard.
 *
 * @see BeaconBlockBody
 * @see AttestationData
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#attestation">Attestation
 *     in the spec</a>
 */
@SSZSerializable
public class Attestation {

  /** A bitfield where each bit corresponds to a validator attested to the {@link #data}. */
  @SSZ(maxSizeVar = "spec.MAX_VALIDATORS_PER_COMMITTEE")
  private final Bitlist aggregationBits;
  /** Attestation data object. */
  @SSZ private final AttestationData data;
  /** Proof of custody bitfield. */
  @SSZ(maxSizeVar = "spec.MAX_VALIDATORS_PER_COMMITTEE")
  private final Bitlist custodyBits;
  /** A product of aggregation of signatures from different validators to {@link #data}. */
  @SSZ private final BLSSignature signature;

  public Attestation(
      Bitlist aggregationBits,
      AttestationData data,
      Bitlist custodyBits,
      BLSSignature signature,
      SpecConstants specConstants) {
    this(
        ensureMaxSize(aggregationBits, specConstants),
        data,
        ensureMaxSize(custodyBits, specConstants),
        signature);
  }

  private static Bitlist ensureMaxSize(Bitlist bits, SpecConstants specConstants) {
    return bits.maxSize() == VARIABLE_SIZE
        ? bits.cappedCopy(specConstants.getMaxValidatorsPerCommittee().longValue())
        : bits;
  }

  private Attestation(
      Bitlist aggregationBits, AttestationData data, Bitlist custodyBits, BLSSignature signature) {
    this.aggregationBits = aggregationBits;
    this.data = data;
    this.custodyBits = custodyBits;
    this.signature = signature;
  }

  public AttestationData getData() {
    return data;
  }

  public Bitlist getAggregationBits() {
    return aggregationBits;
  }

  public Bitlist getCustodyBits() {
    return custodyBits;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public Attestation withAggregationBits(Bitlist aggregationBits, SpecConstants specConstants) {
    assert BLSSignature.ZERO.equals(signature);
    return new Attestation(
        ensureMaxSize(aggregationBits, specConstants), data, custodyBits, BLSSignature.ZERO);
  }

  public Attestation withData(AttestationData data) {
    assert BLSSignature.ZERO.equals(signature);
    return new Attestation(aggregationBits, data, custodyBits, BLSSignature.ZERO);
  }

  public Attestation withCustodyBits(Bitlist custodyBits, SpecConstants specConstants) {
    assert BLSSignature.ZERO.equals(signature);
    return new Attestation(
        aggregationBits, data, ensureMaxSize(custodyBits, specConstants), BLSSignature.ZERO);
  }

  public Attestation withSignature(BLSSignature signature) {
    assert BLSSignature.ZERO.equals(signature);
    return new Attestation(aggregationBits, data, custodyBits, signature);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Attestation that = (Attestation) o;
    return Objects.equal(data, that.data)
        && Objects.equal(aggregationBits, that.aggregationBits)
        && Objects.equal(custodyBits, that.custodyBits)
        && Objects.equal(signature, that.signature);
  }

  @Override
  public int hashCode() {
    int result = data.hashCode();
    result = 31 * result + aggregationBits.hashCode();
    result = 31 * result + custodyBits.hashCode();
    result = 31 * result + signature.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return toString(null, null);
  }

  private String getSignerIndices() {
    return aggregationBits.getBits().stream().map(i -> "" + i).collect(Collectors.joining("+"));
  }

  public String toString(@Nullable SpecConstants spec, @Nullable Time beaconStart) {
    return "Attestation["
        + data.toString()
        + ", attesters="
        + getSignerIndices()
        + ", custodyBits="
        + custodyBits
        + ", sig="
        + signature
        + "]";
  }

  public String toStringShort(@Nullable SpecConstants spec) {
    return "epoch="
        + getData().getTarget().getEpoch().toString()
        + "/"
        + getData().getCrosslink().getShard().toString()
        + "/"
        + getData().getBeaconBlockRoot().toStringShort()
        + "/"
        + getSignerIndices();
  }
}
