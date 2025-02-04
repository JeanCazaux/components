//============================================================================
//
// Copyright (C) 2006-2023 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
//============================================================================
package org.talend.components.snowflake.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.SchemaBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.components.api.exception.ComponentException;
import org.talend.components.common.avro.JDBCAvroRegistry.JDBCConverter;
import org.talend.daikon.avro.AvroUtils;
import org.talend.daikon.avro.SchemaConstants;

import com.google.common.collect.ImmutableMap;

/**
 * Unit-tests for {@link SnowflakeAvroRegistry} class
 */
public class SnowflakeAvroRegistryTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnowflakeAvroRegistryTest.class);

    private static final String TALEND_EXPECTED_DATE_PATTERN = "yyyy-MM-dd";

    private static final String TALEND_EXPECTED_TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
    
    private static final String TALEND_EXPECTED_TIME_PATTERN = "HH:mm:ss";

    private static final String FIELD_NAME = "fieldName";

    private static final String DB_COLUMN_NAME = "dbColumnName";

    private static final String DEFAULT_VALUE = "defaultValue";

    private SnowflakeAvroRegistry snowflakeAvroRegistry;

    private int size;

    private int scale = 5;

    private boolean nullable;

    private Map<Integer, Schema> testPairsForAvroTypes = ImmutableMap.of(Types.VARCHAR, AvroUtils._string(), Types.DECIMAL,
            AvroUtils._decimal(), Types.DOUBLE, AvroUtils._double(), Types.BOOLEAN, AvroUtils._boolean(), Types.JAVA_OBJECT,
            AvroUtils._string());

    @Before
    public void setUp() throws Exception {
        snowflakeAvroRegistry = SnowflakeAvroRegistry.get();
        size = 10;
        scale = 5;
        nullable = true;
    }

    /**
     * Check if sql types converted properly to Avro types
     */
    @Test
    public void testSqlType2AvroString() {
        for (Map.Entry<Integer, Schema> entry : testPairsForAvroTypes.entrySet()) {
            Schema.Field field = snowflakeAvroRegistry
                    .sqlType2Avro(size, scale, entry.getKey(), nullable, FIELD_NAME, DB_COLUMN_NAME, DEFAULT_VALUE);

            Assert.assertEquals(FIELD_NAME, field.name());
            Assert.assertEquals(-1, field.pos());
            Assert.assertEquals(entry.getKey(), field.getObjectProp(SchemaConstants.TALEND_COLUMN_DB_TYPE));
            Assert.assertEquals(DB_COLUMN_NAME, field.getObjectProp(SchemaConstants.TALEND_COLUMN_DB_COLUMN_NAME));
            Assert.assertTrue(AvroUtils.isSameType(AvroUtils.unwrapIfNullable(field.schema()), entry.getValue()));
        }
    }

    /**
     * Check if sql types converted properly to Avro types with right properties setting
     */
    @Test
    public void testSqlType4BasicProperties() {
        for (Map.Entry<Integer, Schema> entry : testPairsForAvroTypes.entrySet()) {
            Schema.Field field = snowflakeAvroRegistry
                    .sqlType2Avro(size, scale, entry.getKey(), nullable, FIELD_NAME, DB_COLUMN_NAME, DEFAULT_VALUE);

            Assert.assertEquals(FIELD_NAME, field.name());
            Assert.assertEquals(-1, field.pos());
            Assert.assertEquals(entry.getKey(), field.getObjectProp(SchemaConstants.TALEND_COLUMN_DB_TYPE));
            Assert.assertEquals(DB_COLUMN_NAME, field.getObjectProp(SchemaConstants.TALEND_COLUMN_DB_COLUMN_NAME));
            Assert.assertTrue(AvroUtils.isSameType(AvroUtils.unwrapIfNullable(field.schema()), entry.getValue()));

            //need to use getProp as now studio use that, so the value must be string, if not, will return null
            switch (entry.getKey()) {
                case Types.VARCHAR:
                    Assert.assertEquals(String.valueOf(size), field.getProp(SchemaConstants.TALEND_COLUMN_DB_LENGTH));
                    break;
                case Types.DECIMAL:
                    Assert.assertEquals(String.valueOf(size), field.getProp(SchemaConstants.TALEND_COLUMN_DB_LENGTH));
                    Assert.assertEquals(String.valueOf(scale), field.getProp(SchemaConstants.TALEND_COLUMN_PRECISION));
                    break;
            }

            Assert.assertEquals(DEFAULT_VALUE, field.getProp(SchemaConstants.TALEND_COLUMN_DEFAULT));
        }
    }

    /**
     * Checks {@link SnowflakeAvroRegistry#sqlType2Avro(int, int, int, boolean, String, String, Object)}
     * returns the {@link org.apache.avro.Schema.Field} with logical DATE type
     */
    @Test
    public void testSqlType2AvroDate() throws Exception {
        final int dbtype = java.sql.Types.DATE;

        Schema.Field field = snowflakeAvroRegistry
                .sqlType2Avro(size, scale, dbtype, nullable, FIELD_NAME, DB_COLUMN_NAME, DEFAULT_VALUE);

        LOGGER.debug("field: " + field.toString());

        Assert.assertEquals(FIELD_NAME, field.name());
        Assert.assertEquals(-1, field.pos());
        Assert.assertEquals(TALEND_EXPECTED_DATE_PATTERN, field.getObjectProp(SchemaConstants.TALEND_COLUMN_PATTERN));
        Assert.assertEquals(java.sql.Types.DATE, field.getObjectProp(SchemaConstants.TALEND_COLUMN_DB_TYPE));
        Assert.assertEquals(DB_COLUMN_NAME, field.getObjectProp(SchemaConstants.TALEND_COLUMN_DB_COLUMN_NAME));
        Assert.assertEquals(DEFAULT_VALUE, field.getObjectProp(SchemaConstants.TALEND_COLUMN_DEFAULT));

        LOGGER.debug(field.getObjectProps().toString());

    }
    
    /**
     * Checks {@link SnowflakeAvroRegistry#sqlType2Avro(int, int, int, boolean, String, String, Object)}
     * returns the {@link org.apache.avro.Schema.Field} with logical TIME type
     */
    @Test
    public void testSqlType2AvroTime() throws Exception {
        final int dbtype = java.sql.Types.TIME;

        Schema.Field field = snowflakeAvroRegistry
                .sqlType2Avro(size, scale, dbtype, nullable, FIELD_NAME, DB_COLUMN_NAME, DEFAULT_VALUE);

        LOGGER.debug("field: " + field.toString());

        Assert.assertEquals(FIELD_NAME, field.name());
        Assert.assertEquals(-1, field.pos());
        Assert.assertEquals(TALEND_EXPECTED_TIME_PATTERN, field.getObjectProp(SchemaConstants.TALEND_COLUMN_PATTERN));
        Assert.assertEquals(java.sql.Types.TIME, field.getObjectProp(SchemaConstants.TALEND_COLUMN_DB_TYPE));
        Assert.assertEquals(DB_COLUMN_NAME, field.getObjectProp(SchemaConstants.TALEND_COLUMN_DB_COLUMN_NAME));
        Assert.assertEquals(DEFAULT_VALUE, field.getObjectProp(SchemaConstants.TALEND_COLUMN_DEFAULT));

        LOGGER.debug(field.getObjectProps().toString());

    }

    /**
     * Checks {@link SnowflakeAvroRegistry#sqlType2Avro(int, int, int, boolean, String, String, Object)}
     * returns the {@link org.apache.avro.Schema.Field} with logical TIMESTAMP type
     */
    @Test
    public void testSqlType2AvroTimestamp() throws Exception {
        final int dbtype = java.sql.Types.TIMESTAMP;

        Schema.Field field = snowflakeAvroRegistry
                .sqlType2Avro(size, scale, dbtype, nullable, FIELD_NAME, DB_COLUMN_NAME, DEFAULT_VALUE);

        LOGGER.debug("field: " + field.toString());

        Assert.assertEquals(FIELD_NAME, field.name());
        Assert.assertEquals(-1, field.pos());
        Assert.assertEquals(TALEND_EXPECTED_TIMESTAMP_PATTERN, field.getObjectProp(SchemaConstants.TALEND_COLUMN_PATTERN));
        Assert.assertEquals(java.sql.Types.TIMESTAMP, field.getObjectProp(SchemaConstants.TALEND_COLUMN_DB_TYPE));
        Assert.assertEquals(DB_COLUMN_NAME, field.getObjectProp(SchemaConstants.TALEND_COLUMN_DB_COLUMN_NAME));
        Assert.assertEquals(DEFAULT_VALUE, field.getObjectProp(SchemaConstants.TALEND_COLUMN_DEFAULT));

        LOGGER.debug(field.getObjectProps().toString());

    }

    /**
     * should never depend on avro defaultVal() method as the migration risk
     *
     */
    @Test
    public void testWrap() {
        Schema schema = SchemaBuilder.builder().record("record").fields().requiredString("column1").endRecord();

        Field field = snowflakeAvroRegistry.wrap("nullableRecord", schema, true, null);
        Assert.assertEquals("nullableRecord", field.name());
        assertNull(field.defaultVal());

        field = snowflakeAvroRegistry.wrap("nullableRecord", schema, false, null);
        assertNull(field.defaultVal());

        field = snowflakeAvroRegistry.wrap("nullableRecord", schema, true, "");
        Assert.assertEquals(null, field.defaultVal());

        field = snowflakeAvroRegistry.wrap("nullableRecord", schema, false, 10);
        Assert.assertEquals(null, field.defaultVal());

    }

    @Test
    public void testGetConverterForDate() throws SQLException {
        Integer dayIntValue = 17_331;
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getObject(0)).thenReturn(dayIntValue);
        Mockito.when(rs.getInt(0)).thenReturn(dayIntValue);
        Schema.Field field = snowflakeAvroRegistry
                .sqlType2Avro(size, scale, Types.DATE, nullable, FIELD_NAME, DB_COLUMN_NAME, null);
        JDBCConverter dateJDBCConverter = snowflakeAvroRegistry.getConverter(field);
        Assert.assertEquals(dayIntValue, dateJDBCConverter.convertToAvro(rs));
    }

    @Test
    public void testGetConverterForNullDate() throws SQLException {
        Object date = null;
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getObject(0)).thenReturn(date);
        Schema.Field field = snowflakeAvroRegistry
                .sqlType2Avro(size, scale, Types.DATE, nullable, FIELD_NAME, DB_COLUMN_NAME, null);
        JDBCConverter dateJDBCConverter = snowflakeAvroRegistry.getConverter(field);
        Assert.assertEquals(date, dateJDBCConverter.convertToAvro(rs));
    }

    @Test
    public void testGetConverterForMillis() throws SQLException {
        Integer millisIntValue = 49_435_000;
        Time time = new Time(millisIntValue);
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getTime(0)).thenReturn(time);
        Schema.Field field = snowflakeAvroRegistry
                .sqlType2Avro(size, scale, Types.TIME, nullable, FIELD_NAME, DB_COLUMN_NAME, null);
        JDBCConverter dateJDBCConverter = snowflakeAvroRegistry.getConverter(field);
        Assert.assertEquals(millisIntValue, dateJDBCConverter.convertToAvro(rs));
    }

    @Test
    public void testGetConverterForTimeStamp() throws SQLException {
        Long millisIntValue = 49_435_000L;
        Timestamp timestamp = new Timestamp(millisIntValue);
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getTimestamp(0)).thenReturn(timestamp);
        Schema.Field field = snowflakeAvroRegistry
                .sqlType2Avro(size, scale, Types.TIMESTAMP, nullable, FIELD_NAME, DB_COLUMN_NAME, null);
        JDBCConverter dateJDBCConverter = snowflakeAvroRegistry.getConverter(field);
        Assert.assertEquals(millisIntValue, dateJDBCConverter.convertToAvro(rs));
    }

    @Test(expected = ComponentException.class)
    public void testGetConverterWithThrownException() throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getTimestamp(0)).thenThrow(new SQLException("Can't get timestamp value from RS"));
        Schema.Field field = snowflakeAvroRegistry
                .sqlType2Avro(size, scale, Types.TIMESTAMP, nullable, FIELD_NAME, DB_COLUMN_NAME, null);
        snowflakeAvroRegistry.getConverter(field).convertToAvro(rs);
    }

    @Test
    public void testGetConverterForInt() throws SQLException {
        BigDecimal value = new BigDecimal(49);
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getObject(0)).thenReturn(value);
        Mockito.when(rs.getBigDecimal(0)).thenReturn(value);
        Schema.Field field = snowflakeAvroRegistry
                .sqlType2Avro(size, scale, Types.INTEGER, nullable, FIELD_NAME, DB_COLUMN_NAME, null);
        JDBCConverter dateJDBCConverter = snowflakeAvroRegistry.getConverter(field);
        Assert.assertEquals(value, dateJDBCConverter.convertToAvro(rs));
    }

    @Test
    public void testDefaultValueForNotNullableColumns() {
        Field f = snowflakeAvroRegistry.sqlType2Avro(1, 1, java.sql.Types.DOUBLE, false, "anyName", "anyName", null);
        //f.defaultVal() have type check now, hard to match it
        assertNull(f.getProp(SchemaConstants.TALEND_COLUMN_DEFAULT));
    }

    @Test
    public void testDefaultValueNotChangedForNotNullableVarchar() {
        String snowflakeEmptyDefaultValue = "";
        Field f = snowflakeAvroRegistry.sqlType2Avro(1, 1, Types.VARCHAR, false, "anyName", "anyName", snowflakeEmptyDefaultValue);

        //f.defaultVal() have type check now, hard to match it
        assertEquals(snowflakeEmptyDefaultValue, f.getProp(SchemaConstants.TALEND_COLUMN_DEFAULT));

    }
}
