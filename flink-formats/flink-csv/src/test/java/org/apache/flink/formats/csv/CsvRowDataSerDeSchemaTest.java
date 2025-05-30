/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.formats.csv;

import org.apache.flink.api.common.typeutils.base.VoidSerializer;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.util.DataFormatConverters;
import org.apache.flink.table.runtime.typeutils.InternalTypeInfo;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.types.Row;
import org.apache.flink.util.InstantiationUtil;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.function.Consumer;

import static org.apache.flink.connector.testutils.formats.SchemaTestUtils.open;
import static org.apache.flink.core.testutils.FlinkAssertions.anyCauseMatches;
import static org.apache.flink.table.api.DataTypes.ARRAY;
import static org.apache.flink.table.api.DataTypes.BIGINT;
import static org.apache.flink.table.api.DataTypes.BOOLEAN;
import static org.apache.flink.table.api.DataTypes.BYTES;
import static org.apache.flink.table.api.DataTypes.DATE;
import static org.apache.flink.table.api.DataTypes.DECIMAL;
import static org.apache.flink.table.api.DataTypes.DOUBLE;
import static org.apache.flink.table.api.DataTypes.FIELD;
import static org.apache.flink.table.api.DataTypes.FLOAT;
import static org.apache.flink.table.api.DataTypes.INT;
import static org.apache.flink.table.api.DataTypes.RAW;
import static org.apache.flink.table.api.DataTypes.ROW;
import static org.apache.flink.table.api.DataTypes.SMALLINT;
import static org.apache.flink.table.api.DataTypes.STRING;
import static org.apache.flink.table.api.DataTypes.TIME;
import static org.apache.flink.table.api.DataTypes.TIMESTAMP;
import static org.apache.flink.table.api.DataTypes.TIMESTAMP_LTZ;
import static org.apache.flink.table.api.DataTypes.TINYINT;
import static org.apache.flink.table.data.StringData.fromString;
import static org.apache.flink.table.data.TimestampData.fromInstant;
import static org.apache.flink.table.data.TimestampData.fromLocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests for {@link CsvRowDataDeserializationSchema} and {@link CsvRowDataSerializationSchema}. */
class CsvRowDataSerDeSchemaTest {

    @Test
    void testSerializeDeserialize() throws Exception {
        testNullableField(BIGINT(), "null", null);
        testNullableField(STRING(), "null", null);
        testNullableField(STRING(), "\"This is a test.\"", "This is a test.");
        testNullableField(STRING(), "\"This is a test\n\r.\"", "This is a test\n\r.");
        testNullableField(BOOLEAN(), "true", true);
        testNullableField(BOOLEAN(), "null", null);
        testNullableField(TINYINT(), "124", (byte) 124);
        testNullableField(SMALLINT(), "10000", (short) 10000);
        testNullableField(INT(), "1234567", 1234567);
        testNullableField(BIGINT(), "12345678910", 12345678910L);
        testNullableField(FLOAT(), "0.33333334", 0.33333334f);
        testNullableField(DOUBLE(), "0.33333333332", 0.33333333332d);
        testNullableField(
                DECIMAL(38, 25),
                "1234.0000000000000000000000001",
                new BigDecimal("1234.0000000000000000000000001"));
        testNullableField(
                DECIMAL(38, 0),
                "123400000000000000000000000001",
                new BigDecimal("123400000000000000000000000001"));
        testNullableField(DATE(), "2018-10-12", Date.valueOf("2018-10-12"));
        testNullableField(TIME(0), "12:12:12", Time.valueOf("12:12:12"));
        testNullableField(
                TIMESTAMP(0),
                "\"2018-10-12 12:12:12\"",
                LocalDateTime.parse("2018-10-12T12:12:12"));
        testNullableField(
                TIMESTAMP(0),
                "\"2018-10-12 12:12:12.123\"",
                LocalDateTime.parse("2018-10-12T12:12:12.123"));
        testNullableField(TIMESTAMP_LTZ(0), "\"1970-01-01 00:02:03Z\"", Instant.ofEpochSecond(123));
        testNullableField(
                TIMESTAMP_LTZ(0), "\"1970-01-01 00:02:03.456Z\"", Instant.ofEpochMilli(123456));
        testNullableField(
                ROW(FIELD("f0", STRING()), FIELD("f1", INT()), FIELD("f2", BOOLEAN())),
                "Hello;42;false",
                Row.of("Hello", 42, false));
        testNullableField(ARRAY(STRING()), "a;b;c", new String[] {"a", "b", "c"});
        testNullableField(ARRAY(TINYINT()), "12;4;null", new Byte[] {12, 4, null});
        testNullableField(BYTES(), "awML", new byte[] {107, 3, 11});
        testNullableField(TIME(3), "12:12:12.232", LocalTime.parse("12:12:12.232"));
        testNullableField(TIME(2), "12:12:12.23", LocalTime.parse("12:12:12.23"));
        testNullableField(TIME(1), "12:12:12.2", LocalTime.parse("12:12:12.2"));
        testNullableField(TIME(0), "12:12:12", LocalTime.parse("12:12:12"));
    }

    @Test
    void testSerializeDeserializeCustomizedProperties() throws Exception {

        Consumer<CsvRowDataSerializationSchema.Builder> serConfig =
                (serSchemaBuilder) ->
                        serSchemaBuilder
                                .setEscapeCharacter('*')
                                .setQuoteCharacter('\'')
                                .setArrayElementDelimiter(":")
                                .setFieldDelimiter(';');

        Consumer<CsvRowDataDeserializationSchema.Builder> deserConfig =
                (deserSchemaBuilder) ->
                        deserSchemaBuilder
                                .setEscapeCharacter('*')
                                .setQuoteCharacter('\'')
                                .setArrayElementDelimiter(":")
                                .setFieldDelimiter(';');

        testFieldDeserialization(STRING(), "123*'4**", "123'4*", deserConfig, ";");
        testField(STRING(), "'123''4**'", "'123''4**'", serConfig, deserConfig, ";");
        testFieldDeserialization(STRING(), "'a;b*'c'", "a;b'c", deserConfig, ";");
        testField(STRING(), "'a;b''c'", "a;b'c", serConfig, deserConfig, ";");
        testFieldDeserialization(INT(), "       12          ", 12, deserConfig, ";");
        testField(INT(), "12", 12, serConfig, deserConfig, ";");
        testFieldDeserialization(
                ROW(FIELD("f0", STRING()), FIELD("f1", STRING())),
                "1:hello",
                Row.of("1", "hello"),
                deserConfig,
                ";");
        testField(
                ROW(FIELD("f0", STRING()), FIELD("f1", STRING())),
                "'1:hello'",
                Row.of("1", "hello"),
                serConfig,
                deserConfig,
                ";");
        testField(
                ROW(FIELD("f0", STRING()), FIELD("f1", STRING())),
                "'1:hello world'",
                Row.of("1", "hello world"),
                serConfig,
                deserConfig,
                ";");
        testField(
                STRING(),
                "null",
                "null",
                serConfig,
                deserConfig,
                ";"); // string because null literal has not been set
        testFieldDeserialization(
                TIME(3), "12:12:12.232", LocalTime.parse("12:12:12.232"), deserConfig, ";");
        testFieldDeserialization(
                TIME(3), "12:12:12.232342", LocalTime.parse("12:12:12.232"), deserConfig, ";");
        testFieldDeserialization(
                TIME(3), "12:12:12.23", LocalTime.parse("12:12:12.23"), deserConfig, ";");
        testFieldDeserialization(
                TIME(2), "12:12:12.23", LocalTime.parse("12:12:12.23"), deserConfig, ";");
        testFieldDeserialization(
                TIME(2), "12:12:12.232312", LocalTime.parse("12:12:12.23"), deserConfig, ";");
        testFieldDeserialization(
                TIME(2), "12:12:12.2", LocalTime.parse("12:12:12.2"), deserConfig, ";");
        testFieldDeserialization(
                TIME(1), "12:12:12.2", LocalTime.parse("12:12:12.2"), deserConfig, ";");
        testFieldDeserialization(
                TIME(1), "12:12:12.2235", LocalTime.parse("12:12:12.2"), deserConfig, ";");
        testFieldDeserialization(
                TIME(1), "12:12:12", LocalTime.parse("12:12:12"), deserConfig, ";");
        testFieldDeserialization(
                TIME(0), "12:12:12", LocalTime.parse("12:12:12"), deserConfig, ";");
        testFieldDeserialization(
                TIME(0), "12:12:12.45", LocalTime.parse("12:12:12"), deserConfig, ";");
        int precision = 5;
        assertThatThrownBy(
                        () ->
                                testFieldDeserialization(
                                        TIME(precision),
                                        "12:12:12.45",
                                        LocalTime.parse("12:12:12"),
                                        deserConfig,
                                        ";"))
                .hasMessage(
                        "Csv does not support TIME type with precision: 5, it only supports precision 0 ~ 3.");
    }

    @Test
    void testDeserializeParseError() {
        assertThatThrownBy(() -> testDeserialization(false, false, "Test,null,Test"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testDeserializeUnsupportedNull() throws Exception {
        // unsupported null for integer
        assertThat(testDeserialization(true, false, "Test,null,Test"))
                .isEqualTo(Row.of("Test", null, "Test"));
    }

    @Test
    void testDeserializeNullRow() throws Exception {
        // return null for null input
        assertThat(testDeserialization(false, false, null)).isNull();
    }

    @Test
    void testDeserializeIncompleteRow() throws Exception {
        // last two columns are missing
        assertThat(testDeserialization(true, false, "Test")).isEqualTo(Row.of("Test", null, null));
    }

    @Test
    void testDeserializeMoreColumnsThanExpected() throws Exception {
        // one additional string column
        assertThat(testDeserialization(true, false, "Test,12,Test,Test")).isNull();
    }

    @Test
    void testDeserializeIgnoreComment() throws Exception {
        // # is part of the string
        assertThat(testDeserialization(false, false, "#Test,12,Test"))
                .isEqualTo(Row.of("#Test", 12, "Test"));
    }

    @Test
    void testDeserializeAllowComment() throws Exception {
        // entire row is ignored
        assertThat(testDeserialization(true, true, "#Test,12,Test")).isNull();
    }

    @Test
    void testSerializationProperties() throws Exception {
        DataType dataType = ROW(FIELD("f0", STRING()), FIELD("f1", INT()), FIELD("f2", STRING()));
        RowType rowType = (RowType) dataType.getLogicalType();
        CsvRowDataSerializationSchema.Builder serSchemaBuilder =
                new CsvRowDataSerializationSchema.Builder(rowType);

        assertThat(serialize(serSchemaBuilder, rowData("Test", 12, "Hello")))
                .isEqualTo("Test,12,Hello".getBytes());

        serSchemaBuilder.setQuoteCharacter('#');

        assertThat(serialize(serSchemaBuilder, rowData("Test", 12, "2019-12-26 12:12:12")))
                .isEqualTo("Test,12,#2019-12-26 12:12:12#".getBytes());

        serSchemaBuilder.disableQuoteCharacter();

        assertThat(serialize(serSchemaBuilder, rowData("Test", 12, "2019-12-26 12:12:12")))
                .isEqualTo("Test,12,2019-12-26 12:12:12".getBytes());
    }

    @Test
    void testInvalidNesting() {
        assertThatThrownBy(
                        () ->
                                testNullableField(
                                        ROW(FIELD("f0", ROW(FIELD("f0", STRING())))),
                                        "FAIL",
                                        Row.of(Row.of("FAIL"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidType() {
        assertThatThrownBy(
                        () ->
                                testNullableField(
                                        RAW(Void.class, VoidSerializer.INSTANCE),
                                        "FAIL",
                                        new java.util.Date()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSerializeDeserializeNestedTypes() throws Exception {
        DataType subDataType0 =
                ROW(
                        FIELD("f0c0", STRING()),
                        FIELD("f0c1", INT()),
                        FIELD("f0c2", STRING()),
                        FIELD("f0c3", TIMESTAMP()),
                        FIELD("f0c4", TIMESTAMP_LTZ()));
        DataType subDataType1 =
                ROW(
                        FIELD("f1c0", STRING()),
                        FIELD("f1c1", INT()),
                        FIELD("f1c2", STRING()),
                        FIELD("f0c3", TIMESTAMP()),
                        FIELD("f0c4", TIMESTAMP_LTZ()));
        DataType dataType = ROW(FIELD("f0", subDataType0), FIELD("f1", subDataType1));
        RowType rowType = (RowType) dataType.getLogicalType();

        // serialization
        CsvRowDataSerializationSchema.Builder serSchemaBuilder =
                new CsvRowDataSerializationSchema.Builder(rowType);
        // deserialization
        CsvRowDataDeserializationSchema.Builder deserSchemaBuilder =
                new CsvRowDataDeserializationSchema.Builder(rowType, InternalTypeInfo.of(rowType));

        RowData normalRow =
                GenericRowData.of(
                        rowData(
                                "hello",
                                1,
                                "This is 1st top column",
                                LocalDateTime.parse("1970-01-01T01:02:03"),
                                Instant.ofEpochMilli(1000)),
                        rowData(
                                "world",
                                2,
                                "This is 2nd top column",
                                LocalDateTime.parse("1970-01-01T01:02:04"),
                                Instant.ofEpochMilli(2000)));
        testSerDeConsistency(normalRow, serSchemaBuilder, deserSchemaBuilder);

        RowData nullRow =
                GenericRowData.of(
                        null,
                        rowData(
                                "world",
                                2,
                                "This is 2nd top column after null",
                                LocalDateTime.parse("1970-01-01T01:02:05"),
                                Instant.ofEpochMilli(3000)));
        testSerDeConsistency(nullRow, serSchemaBuilder, deserSchemaBuilder);
    }

    @Test
    void testDeserializationWithDisableQuoteCharacter() throws Exception {
        Consumer<CsvRowDataDeserializationSchema.Builder> deserConfig =
                (deserSchemaBuilder) ->
                        deserSchemaBuilder.disableQuoteCharacter().setFieldDelimiter(',');

        testFieldDeserialization(STRING(), "\"abc", "\"abc", deserConfig, ",");
    }

    @Test
    void testSerializationWithTypesMismatch() {
        DataType dataType = ROW(FIELD("f0", STRING()), FIELD("f1", INT()), FIELD("f2", INT()));
        RowType rowType = (RowType) dataType.getLogicalType();
        CsvRowDataSerializationSchema.Builder serSchemaBuilder =
                new CsvRowDataSerializationSchema.Builder(rowType);
        RowData rowData = rowData("Test", 1, "Test");
        String errorMessage = "Fail to serialize at field: f2.";
        assertThatThrownBy(() -> serialize(serSchemaBuilder, rowData))
                .satisfies(anyCauseMatches(errorMessage));
    }

    @Test
    void testDeserializationWithTypesMismatch() {
        DataType dataType = ROW(FIELD("f0", STRING()), FIELD("f1", INT()), FIELD("f2", INT()));
        RowType rowType = (RowType) dataType.getLogicalType();
        CsvRowDataDeserializationSchema.Builder deserSchemaBuilder =
                new CsvRowDataDeserializationSchema.Builder(rowType, InternalTypeInfo.of(rowType));
        String data = "Test,1,Test";
        String errorMessage = "Fail to deserialize at field: f2.";
        assertThatThrownBy(() -> deserialize(deserSchemaBuilder, data))
                .satisfies(anyCauseMatches(errorMessage));
    }

    private void testNullableField(DataType fieldType, String string, Object value)
            throws Exception {
        testField(
                fieldType,
                string,
                value,
                (deserSchema) -> deserSchema.setNullLiteral("null"),
                (serSchema) -> serSchema.setNullLiteral("null"),
                ",");
    }

    private void testField(
            DataType fieldType,
            String csvValue,
            Object value,
            Consumer<CsvRowDataSerializationSchema.Builder> serializationConfig,
            Consumer<CsvRowDataDeserializationSchema.Builder> deserializationConfig,
            String fieldDelimiter)
            throws Exception {
        RowType rowType =
                (RowType)
                        ROW(FIELD("f0", STRING()), FIELD("f1", fieldType), FIELD("f2", STRING()))
                                .getLogicalType();
        String expectedCsv = "BEGIN" + fieldDelimiter + csvValue + fieldDelimiter + "END";

        // deserialization
        CsvRowDataDeserializationSchema.Builder deserSchemaBuilder =
                new CsvRowDataDeserializationSchema.Builder(rowType, InternalTypeInfo.of(rowType));
        deserializationConfig.accept(deserSchemaBuilder);
        RowData deserializedRow = deserialize(deserSchemaBuilder, expectedCsv);

        // serialization
        CsvRowDataSerializationSchema.Builder serSchemaBuilder =
                new CsvRowDataSerializationSchema.Builder(rowType);
        serializationConfig.accept(serSchemaBuilder);
        byte[] serializedRow = serialize(serSchemaBuilder, deserializedRow);
        assertThat(new String(serializedRow)).isEqualTo(expectedCsv);
    }

    @SuppressWarnings("unchecked")
    private void testFieldDeserialization(
            DataType fieldType,
            String csvValue,
            Object value,
            Consumer<CsvRowDataDeserializationSchema.Builder> deserializationConfig,
            String fieldDelimiter)
            throws Exception {
        DataType dataType =
                ROW(FIELD("f0", STRING()), FIELD("f1", fieldType), FIELD("f2", STRING()));
        RowType rowType = (RowType) dataType.getLogicalType();
        String csv = "BEGIN" + fieldDelimiter + csvValue + fieldDelimiter + "END";
        Row expectedRow = Row.of("BEGIN", value, "END");

        // deserialization
        CsvRowDataDeserializationSchema.Builder deserSchemaBuilder =
                new CsvRowDataDeserializationSchema.Builder(rowType, InternalTypeInfo.of(rowType));
        deserializationConfig.accept(deserSchemaBuilder);
        RowData deserializedRow = deserialize(deserSchemaBuilder, csv);
        Row actualRow =
                (Row)
                        DataFormatConverters.getConverterForDataType(dataType)
                                .toExternal(deserializedRow);
        assertThat(actualRow).isEqualTo(expectedRow);
    }

    @SuppressWarnings("unchecked")
    private Row testDeserialization(
            boolean allowParsingErrors, boolean allowComments, String string) throws Exception {
        DataType dataType = ROW(FIELD("f0", STRING()), FIELD("f1", INT()), FIELD("f2", STRING()));
        RowType rowType = (RowType) dataType.getLogicalType();
        CsvRowDataDeserializationSchema.Builder deserSchemaBuilder =
                new CsvRowDataDeserializationSchema.Builder(rowType, InternalTypeInfo.of(rowType))
                        .setIgnoreParseErrors(allowParsingErrors)
                        .setAllowComments(allowComments);
        RowData deserializedRow = deserialize(deserSchemaBuilder, string);
        return (Row)
                DataFormatConverters.getConverterForDataType(dataType).toExternal(deserializedRow);
    }

    private void testSerDeConsistency(
            RowData originalRow,
            CsvRowDataSerializationSchema.Builder serSchemaBuilder,
            CsvRowDataDeserializationSchema.Builder deserSchemaBuilder)
            throws Exception {
        RowData deserializedRow =
                deserialize(
                        deserSchemaBuilder, new String(serialize(serSchemaBuilder, originalRow)));
        assertThat(originalRow).isEqualTo(deserializedRow);
    }

    private static byte[] serialize(
            CsvRowDataSerializationSchema.Builder serSchemaBuilder, RowData row) throws Exception {
        // we serialize and deserialize the schema to test runtime behavior
        // when the schema is shipped to the cluster
        CsvRowDataSerializationSchema schema =
                InstantiationUtil.deserializeObject(
                        InstantiationUtil.serializeObject(serSchemaBuilder.build()),
                        CsvRowDataSerDeSchemaTest.class.getClassLoader());
        open(schema);
        return schema.serialize(row);
    }

    private static RowData deserialize(
            CsvRowDataDeserializationSchema.Builder deserSchemaBuilder, String csv)
            throws Exception {
        // we serialize and deserialize the schema to test runtime behavior
        // when the schema is shipped to the cluster
        CsvRowDataDeserializationSchema schema =
                InstantiationUtil.deserializeObject(
                        InstantiationUtil.serializeObject(deserSchemaBuilder.build()),
                        CsvRowDataSerDeSchemaTest.class.getClassLoader());
        open(schema);
        return schema.deserialize(csv != null ? csv.getBytes() : null);
    }

    private static RowData rowData(String str1, int integer, String str2) {
        return GenericRowData.of(fromString(str1), integer, fromString(str2));
    }

    private static RowData rowData(
            String str1, int integer, String str2, LocalDateTime localDateTime, Instant instant) {
        return GenericRowData.of(
                fromString(str1),
                integer,
                fromString(str2),
                fromLocalDateTime(localDateTime),
                fromInstant(instant));
    }
}
