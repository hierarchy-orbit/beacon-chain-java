package org.ethereum.beacon.db.source.impl;

import org.ethereum.beacon.db.source.HoleyList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class HashMapHoleyListTest {

    private HoleyList<String> map;

    @BeforeEach
    public void setUp() {
        map = new HashMapHoleyList<>();
        assertThat(map).isNotNull();
        assertThat(map.size()).isEqualTo(0L);
    }

    @ParameterizedTest
    @MethodSource("keyValueArgumentsProvider")
    public void testPutSize(Long key, String value, Long size) {
        map.put(key, value);
        assertThat(map.size()).isEqualTo(size);
    }

    private static Stream<Arguments> keyValueArgumentsProvider() {
        return Stream.of(
                Arguments.of(0L, "test_value", 1L),
                Arguments.of(0L, null, 0L)
        );
    }

    @ParameterizedTest
    @MethodSource("keyValueGetArgumentsProvider")
    public void testValidGet(Long key, String value) {
        map.put(key, value);
        assertThat(map.get(key)).isPresent().hasValue(value);
    }

    private static Stream<Arguments> keyValueGetArgumentsProvider() {
        return Stream.of(
                Arguments.of(0L, "test_value")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidKeyArgumentsProvider")
    public void testGetOverIndex(Long key, String value, Long wrongKey) {
        map.put(key, value);
        assertThat(map.get(wrongKey)).isNotPresent();
    }

    private static Stream<Arguments> invalidKeyArgumentsProvider() {
        return Stream.of(
                Arguments.of(0L, "test_value", 1L),
                Arguments.of(0L, "test_value", -1L)
        );
    }

    @Test
    public void testPutSameKey() {
        final Long TEST_KEY = 0L;
        final Long TEST_KEY_1 = 1L;
        final String TEST_VALUE = "test_value";
        final String TEST_VALUE_NEW = "NewTestValue";

        map.put(TEST_KEY, TEST_VALUE);
        map.put(TEST_KEY_1, TEST_VALUE);
        assertThat(map.size()).isEqualTo(2L);

        map.put(TEST_KEY, TEST_VALUE_NEW);
        assertThat(map.size()).isEqualTo(2L);
        assertThat(map.get(TEST_KEY)).isPresent().hasValue(TEST_VALUE_NEW);
    }
}
