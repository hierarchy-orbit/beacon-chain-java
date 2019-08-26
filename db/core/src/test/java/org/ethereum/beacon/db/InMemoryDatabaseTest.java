package org.ethereum.beacon.db;

import org.ethereum.beacon.db.source.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.artemis.util.bytes.BytesValue;

import static org.assertj.core.api.Assertions.assertThat;

public class InMemoryDatabaseTest {

    @ParameterizedTest
    @ValueSource(strings = {"TEST_KEY"})
    public void testGetBackingDataSource(String param) {
        final InMemoryDatabase database = new InMemoryDatabase();
        final DataSource<BytesValue, BytesValue> dataSource = database.getBackingDataSource();
        assertThat(dataSource).isNotNull();

        final BytesValue key = BytesValue.wrap(param.getBytes());
        final BytesValue value = BytesValue.EMPTY;
        dataSource.put(key, value);

        assertThat(dataSource.get(key)).isPresent();
    }
}
