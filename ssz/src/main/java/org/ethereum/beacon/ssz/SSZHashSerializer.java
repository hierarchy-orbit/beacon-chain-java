package org.ethereum.beacon.ssz;

import net.consensys.cava.bytes.Bytes;
import org.javatuples.Triplet;

import javax.annotation.Nullable;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ethereum.beacon.ssz.SSZSerializer.checkSSZSerializableAnnotation;

/**
 * Implements Tree Hash algorithm.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/simple-serialize.md#tree-hash">SSZ
 *     Tree Hash</a> in the spec
 */
public class SSZHashSerializer implements BytesHasher, BytesSerializer {

  private static final byte[] EMPTY_PREFIX = SSZSerializer.EMPTY_PREFIX;
  private static final int HASH_LENGTH = 32;

  private SSZSchemeBuilder schemeBuilder;

  private SSZCodecResolver codecResolver;

  /**
   * SSZ hasher with following helpers
   *
   * @param schemeBuilder SSZ model scheme building of type {@link SSZSchemeBuilder.SSZScheme}
   * @param codecResolver Resolves field encoder/decoder {@link
   *     org.ethereum.beacon.ssz.type.SSZCodec} function
   */
  public SSZHashSerializer(SSZSchemeBuilder schemeBuilder, SSZCodecResolver codecResolver) {
    this.schemeBuilder = schemeBuilder;
    this.codecResolver = codecResolver;
  }

  /** Calculates hash of the input object */
  @Override
  public byte[] hash(@Nullable Object input, Class clazz) {
    byte[] preBakedHash;
    if (input instanceof List) {
      preBakedHash = hashList((List) input);
    } else {
      preBakedHash = hashImpl(input, clazz, null);
    }
    // For the final output only (ie. not intermediate outputs), if the output is less than 32
    // bytes, right-zero-pad it to 32 bytes.
    byte[] res;
    if (preBakedHash.length < HASH_LENGTH) {
      res = new byte[HASH_LENGTH];
      System.arraycopy(preBakedHash, 0, res, 0, preBakedHash.length);
    } else {
      res = preBakedHash;
    }

    return res;
  }

  private byte[] hashImpl(@Nullable Object input, Class clazz, @Nullable String truncateField) {
    checkSSZSerializableAnnotation(clazz);

    // Null check
    if (input == null) {
      return EMPTY_PREFIX;
    }

    // Fill up map with all available method getters
    Map<String, Method> getters = new HashMap<>();
    try {
      for (PropertyDescriptor pd : Introspector.getBeanInfo(clazz).getPropertyDescriptors()) {
        getters.put(pd.getReadMethod().getName(), pd.getReadMethod());
      }
    } catch (IntrospectionException e) {
      String error = String.format("Couldn't enumerate all getters in class %s", clazz.getName());
      throw new RuntimeException(error, e);
    }

    // Encode object fields one by one
    SSZSchemeBuilder.SSZScheme fullScheme = schemeBuilder.build(clazz);
    SSZSchemeBuilder.SSZScheme scheme;
    if (truncateField == null) {
      scheme = fullScheme;
    } else {
      scheme = new SSZSchemeBuilder.SSZScheme();
      boolean fieldFound = false;
      for (SSZSchemeBuilder.SSZScheme.SSZField field : fullScheme.fields) {
        if (field.name.equals(truncateField)) {
          fieldFound = true;
          break;
        }
        scheme.fields.add(field);
      }
      if (!fieldFound) {
        throw new RuntimeException(
            String.format("Field %s doesn't exist in object %s", truncateField, input));
      }
    }

    SSZCodecHasher codecHasher = (SSZCodecHasher) codecResolver;
    List<Bytes> containerValues = new ArrayList<>();
    for (SSZSchemeBuilder.SSZScheme.SSZField field : scheme.fields) {
      Object value;
      ByteArrayOutputStream res = new ByteArrayOutputStream();
      Method getter = getters.get(field.getter);
      try {
        if (getter != null) { // We have getter
          value = getter.invoke(input);
        } else { // Trying to access field directly
          value = clazz.getField(field.name).get(input);
        }
      } catch (Exception e) {
        String error =
            String.format(
                "Failed to get value from field %s, your should "
                    + "either have public field or public getter for it",
                field.name);
        throw new SSZSchemeException(error);
      }

      codecResolver.resolveEncodeFunction(field).accept(new Triplet<>(value, res, this));
      containerValues.add(
          codecHasher.zpad(
              codecHasher.hash_tree_root_internal(Bytes.wrap(res.toByteArray())),
              SSZCodecHasher.SSZ_CHUNK_SIZE));
    }

    return codecHasher.merkle_hash(containerValues.toArray(new Bytes[0])).toArray();
  }

  @Override
  public byte[] hashTruncate(@Nullable Object input, Class clazz, String field) {
    byte[] preBakedHash;
    if (input instanceof List) {
      throw new RuntimeException("hashTruncate doesn't support lists");
    } else {
      preBakedHash = hashImpl(input, clazz, field);
    }
    // For the final output only (ie. not intermediate outputs), if the output is less than 32
    // bytes, right-zero-pad it to 32 bytes.
    byte[] res;
    if (preBakedHash.length < HASH_LENGTH) {
      res = new byte[HASH_LENGTH];
      System.arraycopy(preBakedHash, 0, res, 0, preBakedHash.length);
    } else {
      res = preBakedHash;
    }

    return res;
  }

  private byte[] hashList(List input) {
    if (input.isEmpty()) {
      return new byte[0];
    }
    Class internalClass = input.get(0).getClass();
    checkSSZSerializableAnnotation(internalClass);

    // Cook field for such List
    SSZSchemeBuilder.SSZScheme.SSZField field = new SSZSchemeBuilder.SSZScheme.SSZField();
    field.type = internalClass;
    field.multipleType = SSZSchemeBuilder.SSZScheme.MultipleType.LIST;
    field.notAContainer = false;

    ByteArrayOutputStream res = new ByteArrayOutputStream();
    codecResolver.resolveEncodeFunction(field).accept(new Triplet<>(input, res, this));

    return res.toByteArray();
  }

  @Override
  public <C> byte[] encode(@Nullable C input, Class<? extends C> clazz) {
    return hash(input, clazz);
  }

  @Override
  public <C> C decode(byte[] data, Class<? extends C> clazz) {
    throw new RuntimeException("Decode function is not implemented for hash");
  }
}
