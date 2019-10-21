package org.ethereum.beacon.discovery.storage;

import org.ethereum.beacon.chain.storage.impl.SerializerFactory;
import org.ethereum.beacon.db.Database;
import org.ethereum.beacon.discovery.enr.NodeRecord;

import java.util.List;
import java.util.function.Supplier;

public interface NodeTableStorageFactory {
  NodeTableStorage createTable(
      Database database,
      SerializerFactory serializerFactory,
      Supplier<NodeRecord> homeNodeSupplier,
      Supplier<List<NodeRecord>> bootNodes);

  NodeBucketStorage createBucketStorage(
      Database database, SerializerFactory serializerFactory, NodeRecord homeNode);
}