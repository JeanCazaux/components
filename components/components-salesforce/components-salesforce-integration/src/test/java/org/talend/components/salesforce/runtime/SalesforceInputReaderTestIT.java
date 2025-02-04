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
package org.talend.components.salesforce.runtime;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.IndexedRecord;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.components.api.component.ComponentDefinition;
import org.talend.components.api.component.runtime.BoundedReader;
import org.talend.components.api.test.ComponentTestUtils;
import org.talend.components.salesforce.SalesforceConnectionModuleProperties;
import org.talend.components.salesforce.SalesforceOutputProperties;
import org.talend.components.salesforce.integration.SalesforceTestBase;
import org.talend.components.salesforce.tsalesforceinput.TSalesforceInputDefinition;
import org.talend.components.salesforce.tsalesforceinput.TSalesforceInputProperties;
import org.talend.components.salesforce.tsalesforceoutput.TSalesforceOutputDefinition;
import org.talend.components.salesforce.tsalesforceoutput.TSalesforceOutputProperties;
import org.talend.daikon.avro.AvroUtils;
import org.talend.daikon.avro.SchemaConstants;

public class SalesforceInputReaderTestIT extends SalesforceTestBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(SalesforceInputReaderTestIT.class);

    private static String randomizedValue;

    @BeforeClass
    public static void setup() throws Throwable {
        randomizedValue = "Name_IT_" + createNewRandom();

        List<IndexedRecord> outputRows = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            GenericData.Record row = new GenericData.Record(getSchema(false));
            row.put("Name", randomizedValue);
            row.put("ShippingStreet", "123 Main Street");
            row.put("ShippingPostalCode", Integer.toString(i));
            row.put("BillingStreet", "123 Main Street");
            row.put("BillingState", "CA");
            row.put("BillingPostalCode", createNewRandom());
            outputRows.add(row);
        }
        TSalesforceOutputProperties props = (TSalesforceOutputProperties) new TSalesforceOutputProperties("foo").init();
        setupProps(props.connection);
        props.module.moduleName.setValue(EXISTING_MODULE_NAME);
        props.module.main.schema.setValue(getSchema(false));
        doWriteRows(props,outputRows);
    }

    @AfterClass
    public static void cleanup() throws Throwable {
        deleteAllAccountTestRows(randomizedValue);
    }

    public static Schema SCHEMA_QUERY_ACCOUNT = SchemaBuilder.builder().record("Schema").fields() //
            .name("Id").type().stringType().noDefault() //
            .name("Name").type().stringType().noDefault() //
            .name("BillingStreet").type().stringType().noDefault() //
            .name("BillingCity").type().stringType().noDefault() //
            .name("BillingState").type().stringType().noDefault() //
            .name("NumberOfEmployees").type().intType().noDefault() //
            .name("AnnualRevenue").type(AvroUtils._decimal()).noDefault() //
            .name("BillingCountry").type().bytesType().noDefault().endRecord();

    public static Schema SCHEMA_CONTACT = SchemaBuilder.builder().record("Schema").fields() //
            .name("Email").type().stringType().noDefault() //
            .name("FirstName").type().stringType().noDefault() //
            .name("LastName").type().stringType().noDefault() //
            .name("AccountId").type().stringType().noDefault() //
            .endRecord();

    @Test
    public void testStartAdvanceGetCurrent() throws IOException {
        BoundedReader<?> salesforceInputReader = createSalesforceInputReaderFromModule(EXISTING_MODULE_NAME, null);
        try {
            assertTrue(salesforceInputReader.start());
            assertTrue(salesforceInputReader.advance());
            assertNotNull(salesforceInputReader.getCurrent());
        } finally {
            salesforceInputReader.close();
        }
    }

    @Test(expected = IOException.class)
    public void testStartException() throws IOException {
        BoundedReader<IndexedRecord> salesforceInputReader = createSalesforceInputReaderFromModule(
                SalesforceTestBase.NOT_EXISTING_MODULE_NAME, null);
        try {
            assertTrue(salesforceInputReader.start());
        } finally {
            salesforceInputReader.close();
        }
    }

    @Test
    public void testInput() throws Throwable {
        runInputTest(false, false);
    }

    @Test
    public void testInputDynamic() throws Throwable {
        // FIXME - finish this test
        runInputTest(true, false);
    }

    @Test
    public void testInputBulkQuery() throws Throwable {
        runInputTest(false, true);
    }

    @Test
    public void testInputBulkQueryDynamic() throws Throwable {
        runInputTest(true, true);
    }

    @Ignore("Our Salesforce credentials were used too many time in ITs they may create huge amount of data and this test can execute too long")
    @Test
    public void testBulkApiWithPkChunking() throws Throwable {
        TSalesforceInputProperties properties = createTSalesforceInputProperties(false, true);
        properties.manualQuery.setValue(false);

        // Some records can't be erased by deleteAllAccountTestRows(),
        // they have relations to other tables, we need to extract them(count) from main test.
        List<IndexedRecord> readRows = readRows(properties);
        int defaultRecordsInSalesforce = readRows.size();

        properties.pkChunking.setValue(true);
        // This all test were run to many times and created/deleted huge amount of data,
        // to avoid Error: TotalRequests Limit exceeded lets get data with chunk size 100_000(default on Salesforce)
        properties.chunkSize.setValue(TSalesforceInputProperties.DEFAULT_CHUNK_SIZE);
        int count = 1500;
        String random = createNewRandom();
        List<IndexedRecord> outputRows = makeRows(random, count, true);
        outputRows = writeRows(random, properties, outputRows);
        try {
            readRows = readRows(properties);
            LOGGER.info("Read rows count - {}", readRows.size());
            Assert.assertEquals((readRows.size() - defaultRecordsInSalesforce), outputRows.size());
        } finally {
            deleteRows(outputRows, properties);
        }
    }

    @Test
    public void testClosingAlreadyClosedJob() {
        try {
            TSalesforceInputProperties properties = createTSalesforceInputProperties(false, true);
            properties.manualQuery.setValue(false);
            SalesforceBulkQueryInputReader reader = (SalesforceBulkQueryInputReader) this
                    .<IndexedRecord> createBoundedReader(properties);
            reader.start();
            reader.close();
            // Job could be closed on Salesforce side and previously we tried to close it again, we shouldn't do that.
            // We can emulate this like calling close the job second time.
            reader.close();
        } catch (Throwable t) {
            Assert.fail("This test shouldn't throw any errors, since we're closing already closed job");
        }

    }

    protected TSalesforceInputProperties createTSalesforceInputProperties(boolean emptySchema, boolean isBulkQury)
            throws Throwable {
        TSalesforceInputProperties props = (TSalesforceInputProperties) new TSalesforceInputProperties("foo").init(); //$NON-NLS-1$
        props.connection.timeout.setValue(60000);
        props.batchSize.setValue(100);
        if (isBulkQury) {
            props.queryMode.setValue(TSalesforceInputProperties.QueryMode.Bulk);
            props.connection.bulkConnection.setValue(true);
            props.manualQuery.setValue(true);
            props.query.setValue(
                    "select Id,Name,ShippingStreet,ShippingPostalCode,BillingStreet,BillingState,BillingPostalCode from Account");

            setupProps(props.connection);

            props.module.moduleName.setValue(EXISTING_MODULE_NAME);
            props.module.main.schema.setValue(getMakeRowSchema(false));

        } else {
            setupProps(props.connection);
            if (emptySchema) {
                setupModuleWithEmptySchema(props.module, EXISTING_MODULE_NAME);
            } else {
                setupModule(props.module, EXISTING_MODULE_NAME);
            }
        }

        ComponentTestUtils.checkSerialize(props, errorCollector);

        return props;
    }

    protected void runInputTest(boolean emptySchema, boolean isBulkQury) throws Throwable {

        TSalesforceInputProperties props = createTSalesforceInputProperties(emptySchema, isBulkQury);
        String random = createNewRandom();
        int count = 10;
        // store rows in SF to retrieve them afterward to test the input.
        List<IndexedRecord> outputRows = makeRows(random, count, true);
        outputRows = writeRows(random, props, outputRows);
        checkRows(random, outputRows, count);
        try {
            List<IndexedRecord> rows = readRows(props);
            checkRows(random, rows, count);
            // Some tests are duplicates, reuse some test for return all empty value as null test
            testBulkQueryNullValue(props, random, !emptySchema);
        } finally {
            deleteRows(outputRows, props);
        }
    }

    public static Schema getSchema(boolean isDynamic) {
        SchemaBuilder.FieldAssembler<Schema> fa = SchemaBuilder.builder().record("MakeRowRecord").fields() //
                .name("Id").type().nullable().stringType().noDefault() //
                .name("Name").type().nullable().stringType().noDefault() //
                .name("ShippingStreet").type().nullable().stringType().noDefault() //
                .name("ShippingPostalCode").type().nullable().intType().noDefault() //
                .name("BillingStreet").type().nullable().stringType().noDefault() //
                .name("BillingState").type().nullable().stringType().noDefault() //
                .name("BillingPostalCode").type().nullable().stringType().noDefault();
        if (isDynamic) {
            fa = fa.name("ShippingState").type().nullable().stringType().noDefault();
        }

        return fa.endRecord();
    }

    @Override
    public Schema getMakeRowSchema(boolean isDynamic) {
        return getSchema(isDynamic);
    }

    @Test
    public void testManualQuery() throws Throwable {
        TSalesforceInputProperties props = createTSalesforceInputProperties(false, false);
        props.manualQuery.setValue(true);
        props.query.setValue("select Id from Account WHERE Name = '" + randomizedValue + "'");
        List<IndexedRecord> outputRows = readRows(props);
        assertEquals(100, outputRows.size());
        props.module.main.schema.setValue(SchemaBuilder.builder().record("MakeRowRecord").fields()//
                .name("Id").type().nullable().stringType().noDefault() //
                .name("Name").type().nullable().stringType().noDefault() //
                .name("Owner_Name").type().nullable().stringType().noDefault() //
                .name("Owner_Id").type().nullable().stringType().noDefault().endRecord());
        props.query
                .setValue("SELECT Id, Name, Owner.Name ,Owner.Id FROM Account WHERE Name = '" + randomizedValue + "'");
        List<IndexedRecord> rowsWithForeignKey = readRows(props);

        props.module.main.schema.setValue(SchemaBuilder.builder().record("MakeRowRecord").fields()//
                .name("Id").type().nullable().stringType().noDefault() //
                .name("Name").type().nullable().stringType().noDefault() //
                .name("OwnerId").type().nullable().stringType().noDefault().endRecord());
        props.query.setValue("SELECT Id, Name, OwnerId FROM Account WHERE Name = '" + randomizedValue + "'");
        outputRows = readRows(props);

        assertEquals(rowsWithForeignKey.size(), outputRows.size());
        assertEquals(100, rowsWithForeignKey.size());
        IndexedRecord fkRecord = rowsWithForeignKey.get(0);
        IndexedRecord commonRecord = outputRows.get(0);
        assertNotNull(fkRecord);
        assertNotNull(commonRecord);
        Schema schemaFK = fkRecord.getSchema();
        Schema schemaCommon = commonRecord.getSchema();

        assertNotNull(schemaFK);
        assertNotNull(schemaCommon);
        assertEquals(commonRecord.get(schemaCommon.getField("OwnerId").pos()), fkRecord.get(schemaFK.getField("Owner_Id").pos()));

    }

    @Ignore("Need to create a custom big object in test salesforce server")
    @Test
    public void testBigObjectBatchQuery() throws Throwable {
        // 1. Create big object in you salesforce account with any field
        String bigObjectModule = "TestBigObject__b";
        // 2. Write more 230 records into the created big object

        // 3. Query the data with not bulkquery mode with batch 200
        Schema bigObjectSchema = SchemaBuilder.builder()
                .record("MakeRowRecord").fields()//
                .name("Id").type().nullable().stringType().noDefault().endRecord();//
        TSalesforceInputProperties props = createTSalesforceInputProperties(false, false);
        props.batchSize.setValue(200);
        props.module.moduleName.setValue(bigObjectModule);
        props.module.main.schema.setValue(bigObjectSchema);
        // 4. Read records with above configuration
        List<IndexedRecord> outputRows = readRows(props);
        assertEquals(230, outputRows.size());

    }

    @Test
    public void testQueryDeleted() throws Throwable {
        // 1. prepare account records
        String name = "Name_IT_" + createNewRandom();
        List<IndexedRecord> outputRows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            GenericData.Record row = new GenericData.Record(getSchema(false));
            row.put("Name", name);
            row.put("ShippingStreet", "123 Main Street");
            row.put("ShippingPostalCode", Integer.toString(i));
            row.put("BillingStreet", "123 Main Street");
            row.put("BillingState", "CA");
            row.put("BillingPostalCode", createNewRandom());
            outputRows.add(row);
        }
        TSalesforceOutputProperties props = (TSalesforceOutputProperties) new TSalesforceOutputProperties("foo").init();
        setupProps(props.connection);
        props.module.moduleName.setValue(EXISTING_MODULE_NAME);
        props.module.main.schema.setValue(getSchema(false));
        doWriteRows(props,outputRows);
        // 2.check accounts
        String soql = "SELECT Id, Name FROM Account WHERE Name = '" + name + "'";
        TSalesforceInputProperties properties =createTSalesforceInputProperties(false,true);
        List<IndexedRecord> records = checkRows(properties,soql,10,true,false);
        // 3. delete accounts
        deleteRows(records, properties);
        // 4. check include delete checkbox
        checkRows(properties,soql,0,true,false);
        checkRows(properties,soql,10,true,true);

    }

    /**
     * This for basic connection manual query with dynamic
     */
    @Test
    public void testManualQueryDynamic() throws Throwable {
        testManualQueryDynamic(false);
    }

    /**
     * This for basic connection manual query with dynamic
     */
    @Test
    public void testBulkManualQueryDynamic() throws Throwable {
        testManualQueryDynamic(true);
    }

    private final static Schema SCHEMA_INT = SchemaBuilder.builder().record("Schema").fields().name("VALUE").type().intType()
            .noDefault().endRecord();

    private final Schema SCHEMA_DOUBLE = SchemaBuilder.builder().record("Schema").fields().name("VALUE").type().doubleType()
            .noDefault().endRecord();

    private final Schema SCHEMA_STRING = SchemaBuilder.builder().record("Schema").fields().name("VALUE").type().stringType()
            .noDefault().endRecord();

    private final Schema SCHEMA_DATE;
    {
        List<Schema.Field> fields = new ArrayList<>();
        Schema.Field avroField = new Schema.Field("VALUE", AvroUtils.wrapAsNullable(AvroUtils._date()), null, (String)null);
        avroField.addProp(SchemaConstants.TALEND_COLUMN_PATTERN, "yyyy-MM-dd HH");
        fields.add(avroField);
        SCHEMA_DATE = Schema.createRecord("Schema", null, null, false, fields);
    }

    @Test
    public void testAggregrateQueryWithDoubleTypeAndBasicQuery() throws Throwable {
        TSalesforceInputProperties props = createTSalesforceInputProperties(true, false);
        props.manualQuery.setValue(true);
        props.query.setValue("SELECT AVG(Amount) VALUE FROM Opportunity");// alias is necessary and should be the same with schema
        props.module.main.schema.setValue(SCHEMA_DOUBLE);
        List<IndexedRecord> outputRows = readRows(props);
        assertEquals(1, outputRows.size());
        IndexedRecord record = outputRows.get(0);
        assertNotNull(record.getSchema());
        Object value = record.get(0);
        Assert.assertTrue(value != null && value instanceof Double);
    }

    @Test
    public void testAggregrateQueryWithIntTypeAndBasicQuery() throws Throwable {
        TSalesforceInputProperties props = createTSalesforceInputProperties(true, false);
        props.manualQuery.setValue(true);
        props.query.setValue("SELECT COUNT(ID) VALUE FROM Account WHERE Name LIKE 'a%'");// alias is necessary and should be the
                                                                                         // same with schema
        props.module.main.schema.setValue(SCHEMA_INT);
        List<IndexedRecord> outputRows = readRows(props);
        assertEquals(1, outputRows.size());
        IndexedRecord record = outputRows.get(0);
        assertNotNull(record.getSchema());
        Object value = record.get(0);
        Assert.assertTrue(value != null && value instanceof Integer);
    }

    @Test
    public void testAggregrateQueryWithDateTypeAndBasicQuery() throws Throwable {
        TSalesforceInputProperties props = createTSalesforceInputProperties(true, false);
        props.manualQuery.setValue(true);
        props.query.setValue("SELECT MIN(CreatedDate) VALUE FROM Contact GROUP BY FirstName, LastName LIMIT 1");// alias is
                                                                                                        // necessary
                                                                                                        // and
                                                                                                        // should
                                                                                                        // be the
                                                                                                        // same
                                                                                                        // with
                                                                                                        // schema
        props.module.main.schema.setValue(SCHEMA_DATE);
        List<IndexedRecord> outputRows = readRows(props);

        if (outputRows.isEmpty()) {
            return;
        }

        IndexedRecord record = outputRows.get(0);
        assertNotNull(record.getSchema());
        Object value = record.get(0);
        Assert.assertTrue(value != null && value instanceof Long);
    }

    @Test
    public void testAggregrateQueryWithDateTypeAndStringOutputAndBasicQuery() throws Throwable {
        TSalesforceInputProperties props = createTSalesforceInputProperties(true, false);
        props.manualQuery.setValue(true);
        props.query.setValue("SELECT MIN(CreatedDate) VALUE FROM Contact GROUP BY FirstName, LastName LIMIT 1");// alias is
                                                                                                        // necessary
                                                                                                        // and
                                                                                                        // should
                                                                                                        // be the
                                                                                                        // same
                                                                                                        // with
                                                                                                        // schema
        props.module.main.schema.setValue(SCHEMA_STRING);
        List<IndexedRecord> outputRows = readRows(props);

        if (outputRows.isEmpty()) {
            return;
        }

        IndexedRecord record = outputRows.get(0);
        assertNotNull(record.getSchema());
        Object value = record.get(0);
        Assert.assertTrue(value != null && value instanceof String);
    }

    public void testManualQueryDynamic(boolean isBulkQuery) throws Throwable {
        TSalesforceInputProperties props = createTSalesforceInputProperties(true, false);
        props.manualQuery.setValue(true);
        props.query.setValue("select Id,IsDeleted,Name,Phone,CreatedDate from Account limit 1");
        if (isBulkQuery) {
            props.queryMode.setValue(TSalesforceInputProperties.QueryMode.Bulk);
        }
        List<IndexedRecord> outputRows = readRows(props);
        assertEquals(1, outputRows.size());
        IndexedRecord record = outputRows.get(0);
        assertNotNull(record.getSchema());
        Schema.Field field = record.getSchema().getField("CreatedDate");
        assertEquals("yyyy-MM-dd'T'HH:mm:ss'.000Z'", field.getObjectProp(SchemaConstants.TALEND_COLUMN_PATTERN));
    }

    /*
     * Test nested query of SOQL. Checking if data was placed correctly by guessed schema method.
     */
    @Test
    public void testComplexSOQLQuery() throws Throwable {
        TSalesforceInputProperties props = createTSalesforceInputProperties(false, false);
        props.manualQuery.setValue(true);
        // Manual query with foreign key
        // Need to specify where clause to be sure that this record exists and has parent-to-child relation.
        props.query.setValue("Select Id, Name,(Select Contact.Id,Contact.Name from Account.Contacts) from Account WHERE Name = 'United Oil & Gas, UK' Limit 1");
        props.validateGuessSchema();
        List<IndexedRecord> rows = readRows(props);

        if (rows.size() > 0) {
            for (IndexedRecord row : rows) {
                Schema schema = row.getSchema();
                assertNotNull(schema.getField("Id"));
                assertNotNull(schema.getField("Name"));
                assertNotNull(schema.getField("Account_Contacts_records_Contact_Id"));
                assertNotNull(schema.getField("Account_Contacts_records_Contact_Name"));

                assertNotNull(row.get(schema.getField("Id").pos()));
                assertNotNull(row.get(schema.getField("Name").pos()));
                assertNotNull(row.get(schema.getField("Account_Contacts_records_Contact_Id").pos()));
                assertNotNull(row.get(schema.getField("Account_Contacts_records_Contact_Name").pos()));
            }
        } else {
            LOGGER.warn("Query result is empty!");
        }
    }

    /*
     * Test nested query of SOQL. return values with field empty
     */
    @Test
    public void testQueryWithSubquery() throws Throwable {

        // 1.get a Account record Id
        TSalesforceInputProperties props = createTSalesforceInputProperties(false, false);
        props.manualQuery.setValue(true);
        props.query.setValue("select Id from Account where Name = '" + randomizedValue + "' limit 1");
        props.validateGuessSchema();
        List<IndexedRecord> singleAccount = readRows(props);
        assertEquals(1, singleAccount.size());

        String accountId = (String) singleAccount.get(0).get(0);

        // 2.Write Contact records
        ComponentDefinition sfDef = new TSalesforceOutputDefinition();
        TSalesforceOutputProperties sfProps = (TSalesforceOutputProperties) sfDef.createRuntimeProperties();
        SalesforceTestBase.setupProps(sfProps.connection);
        sfProps.module.setValue("moduleName", "Contact");
        sfProps.module.main.schema.setValue(SCHEMA_CONTACT);
        sfProps.outputAction.setValue(SalesforceOutputProperties.OutputAction.INSERT);
        sfProps.ceaseForError.setValue(false);
        sfProps.extendInsert.setValue(false);
        sfProps.retrieveInsertId.setValue(true);
        sfProps.module.schemaListener.afterSchema();

        List records = new ArrayList<IndexedRecord>();
        String random = createNewRandom();
        IndexedRecord r1 = new GenericData.Record(SCHEMA_CONTACT);
        r1.put(0, "aaa" + random + "@talend.com");
        r1.put(1, "F1_" + random);
        r1.put(2, "L1_" + random);
        r1.put(3, accountId);
        records.add(r1);
        IndexedRecord r2 = new GenericData.Record(SCHEMA_CONTACT);
        r2.put(1, "F2_" + random);
        r2.put(2, "L2_" + random);
        r2.put(3, accountId);
        records.add(r2);
        IndexedRecord r3 = new GenericData.Record(SCHEMA_CONTACT);
        r3.put(0, "ccc" + random + "@talend.com");
        r3.put(2, "L3_" + random);
        r3.put(3, accountId);
        records.add(r3);

        doWriteRows(sfProps, records);

        //3. check the Contacts in Account with sub-query
        Schema querySchema = SchemaBuilder.builder().record("Schema").fields() //
                .name("Id").type().stringType().noDefault() //
                .name("Contacts_records_Id").type().stringType().noDefault() //
                .name("Contacts_records_Email").type().stringType().noDefault() //
                .name("Contacts_records_FirstName").type().stringType().noDefault() //
                .name("Contacts_records_LastName").type().stringType().noDefault() //
                .endRecord();
        props.manualQuery.setValue(true);
        props.normalizeDelimiter.setValue("-");
        props.query
                .setValue(
                        "SELECT Id, (select Id, Email,FirstName,LastName from Contacts order by LastName) FROM ACCOUNT where Id = '"
                                + accountId + "'");
        props.validateGuessSchema();
        props.module.main.schema.setValue(querySchema);
        List<IndexedRecord> rows = readRows(props);

        try {
            assertEquals(1, rows.size());
            IndexedRecord record = rows.get(0);
            assertEquals("aaa" + random + "@talend.com-null-ccc" + random + "@talend.com", record.get(2));
            assertEquals("F1_" + random + "-F2_" + random + "-null", record.get(3));
            assertEquals("L1_" + random + "-L2_" + random + "-L3_" + random, record.get(4));
        }finally {
            // 6.Delete created contacts data
            props.copyValuesFrom(sfProps);
            props.manualQuery.setValue(true);
            props.query.setValue("SELECT Id FROM Contact where FirstName like '%" + random + "'");
            props.validateGuessSchema();
            List<IndexedRecord> contacts = readRows(props);
            deleteRows(contacts, props);
        }
    }

    /**
     * Test query mode fields of schema is not case sensitive
     */
    @Test
    public void testColumnNameCaseSensitive() throws Throwable {
        TSalesforceInputProperties props = createTSalesforceInputProperties(false, false);
        Schema schema = SchemaBuilder.builder().record("Schema").fields() //
                .name("ID").type().stringType().noDefault() //
                .name("type").type().stringType().noDefault() //
                .name("NAME").type().stringType().noDefault().endRecord();
        props.module.main.schema.setValue(schema);
        props.condition.setValue("Id != null and name != null and type!=null Limit 1");
        props.validateGuessSchema();
        List<IndexedRecord> rows = readRows(props);

        if (rows.size() > 0) {
            assertEquals(1, rows.size());
            IndexedRecord row = rows.get(0);
            Schema runtimeSchema = row.getSchema();
            assertEquals(3, runtimeSchema.getFields().size());
            assertNotNull(row.get(schema.getField("ID").pos()));
            assertNotNull(row.get(schema.getField("type").pos()));
            assertNotEquals("Account",row.get(schema.getField("type").pos()));
            assertNotNull(row.get(schema.getField("NAME").pos()));
        } else {
            LOGGER.warn("Query result is empty!");
        }
    }

    /**
     * Test aggregate query field not case sensitive
     */
    @Test
    public void testAggregateQueryColumnNameCaseSensitive() throws Throwable {
        TSalesforceInputProperties props = createTSalesforceInputProperties(true, false);
        props.manualQuery.setValue(true);
        props.query.setValue("SELECT MIN(CreatedDate) value FROM Contact GROUP BY FirstName, LastName LIMIT 1");
        props.module.main.schema.setValue(SCHEMA_DATE);
        List<IndexedRecord> outputRows = readRows(props);
        if (outputRows.isEmpty()) {
            return;
        }
        IndexedRecord record = outputRows.get(0);
        assertNotNull(record.getSchema());
        Object value = record.get(0);
        Assert.assertTrue(value != null && value instanceof Long);
    }

    /**
     * Check SOQL include toLabel() in bulk query
     */
    @Test
    public void testSOQLToLabelBulkQuery() throws Throwable {
        TSalesforceInputProperties props = createTSalesforceInputProperties(true, true);
        props.module.moduleName.setValue("Contact");
        props.manualQuery.setValue(true);
        props.query.setValue("SELECT toLabel(Contact.Name), Account.Name from Contact LIMIT 1");
        props.module.main.schema
                .setValue(SchemaBuilder.builder().record("ContactSchema") //
                        .fields() //
                        .name("Name").type().nullable().stringType().noDefault() //
                        .name("Account_NAME").type().nullable().stringType().noDefault() //
                        .endRecord()); //
        try {
            readRows(props);
        } catch (Throwable throwable) {
            fail(throwable.getMessage());
        }
    }

    @Test
    public void testInputNBLine() throws Throwable {
        TSalesforceInputProperties props = createTSalesforceInputProperties(false, false);
        List<IndexedRecord> returnRecords = null;
        String query = "SELECT Id, Name FROM Account WHERE Name = '" + randomizedValue + "'";
        // SOAP query test
        returnRecords = checkRows(props, query, 100, false);
        assertThat(returnRecords.size(), is(100));
        // Bulk query test
        returnRecords = checkRows(props, query, 100, true);
        assertThat(returnRecords.size(), is(100));
    }

    /*
     * Test salesforce input manual query with dynamic return fields order same with SOQL fields order
     */
    @Test
    public void testDynamicFieldsOrder() throws Throwable {
        TSalesforceInputProperties props = createTSalesforceInputProperties(true, false);
        LOGGER.debug(props.module.main.schema.getStringValue());
        props.manualQuery.setValue(true);
        props.query.setValue(
                "Select Name,IsDeleted,Id, Type,ParentId,MasterRecordId ,CreatedDate from Account order by CreatedDate limit 1 ");
        List<IndexedRecord> rows = readRows(props);
        assertEquals("No record returned!", 1, rows.size());
        List<Schema.Field> fields = rows.get(0).getSchema().getFields();
        assertEquals(7, fields.size());
        assertEquals("Name", fields.get(0).name());
        assertEquals("IsDeleted", fields.get(1).name());
        assertEquals("Id", fields.get(2).name());
        assertEquals("Type", fields.get(3).name());
        assertEquals("ParentId", fields.get(4).name());
        assertEquals("MasterRecordId", fields.get(5).name());
        assertEquals("CreatedDate", fields.get(6).name());

    }

    @Test
    public void testQueryWithTypes() throws Throwable {

        Schema test_schema_long = SchemaBuilder.builder().record("Schema").fields() //
                .name("Id").type().stringType().noDefault() //
                .name("NumberOfEmployees").type().longType().noDefault() //
                .name("AnnualRevenue").type(AvroUtils._float()).noDefault().endRecord();

        Schema test_schema_short = SchemaBuilder.builder().record("Schema").fields() //
                .name("Id").type().stringType().noDefault() //
                .name("NumberOfEmployees").type(AvroUtils._short()).noDefault() //
                .name("AnnualRevenue").type(AvroUtils._float()).noDefault().endRecord();

        Schema test_schema_byte= SchemaBuilder.builder().record("Schema").fields() //
                .name("Id").type().stringType().noDefault() //
                .name("NumberOfEmployees").type(AvroUtils._byte()).noDefault() //
                .name("AnnualRevenue").type(AvroUtils._float()).noDefault().endRecord();

        try {
            TSalesforceInputProperties sfInputProps = createTSalesforceInputProperties(false, false);

            sfInputProps.condition.setValue("NumberOfEmployees != null  limit 1");
            // 1.Test long and float type
            sfInputProps.module.main.schema.setValue(test_schema_long);
            List<IndexedRecord> inpuRecords = readRows(sfInputProps);
            IndexedRecord record = null;
            if (inpuRecords.size() < 1) {
                LOGGER.warn("Salesforce default records have been changed!");
            } else {
                record = inpuRecords.get(0);
                Object longValue = record.get(1);
                Object floatValue = record.get(2);
                if (longValue != null) {
                    assertThat(longValue, instanceOf(Long.class));
                }
                if (floatValue != null) {
                    assertThat(floatValue, instanceOf(Float.class));
                }
            }
            // 2.Test short type
            sfInputProps.condition.setValue("NumberOfEmployees = null  limit 1");
            sfInputProps.module.main.schema.setValue(test_schema_short);
            inpuRecords = readRows(sfInputProps);
            if (inpuRecords.size() == 1) {
                record = inpuRecords.get(0);
                assertNull(record.get(1));
            }
            // 3.Test byte type
            sfInputProps.module.main.schema.setValue(test_schema_byte);
            inpuRecords = readRows(sfInputProps);
            if (inpuRecords.size() == 1) {
                record = inpuRecords.get(0);
                assertNull(record.get(1));
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Should catch exception with error message "Entity 'CaseHistory' is not supported to use PKChunking"
     */
    @Test(expected = IOException.class)
    public void testPKChunkingParentObjectError() throws Throwable {
        testPKChunkingParentObject(false);
    }

    /**
     * Test specify the parent object when query on sharing objects.
     */
    @Test()
    public void testPKChunkingParentObjectOK() throws Throwable {
        testPKChunkingParentObject(true);
    }

    /**
     * Test aggregate query field not case sensitive
     */
    public void testPKChunkingParentObject(boolean specifyParent) throws Throwable {
        Schema caseHistorySchema = SchemaBuilder.builder().record("Schema").fields() //
                .name("Id").type().stringType().noDefault() //
                .name("CaseId").type().stringType().noDefault() //
                .name("Field").type().stringType().noDefault() //
                .endRecord();
        TSalesforceInputProperties props = createTSalesforceInputProperties(true, true);
        props.manualQuery.setValue(true);
        props.query.setValue("SELECT Id,CaseId,Field from CaseHistory");
        props.module.main.schema.setValue(caseHistorySchema);
        props.pkChunking.setValue(true);
        props.chunkSize.setValue(1000);
        props.specifyParent.setValue(specifyParent);
        props.parentObject.setValue("Case");
        readRows(props);
    }

    protected void testBulkQueryNullValue(SalesforceConnectionModuleProperties props, String random,boolean returnNullForEmpty) throws Throwable {
        ComponentDefinition sfInputDef = new TSalesforceInputDefinition();
        TSalesforceInputProperties sfInputProps = (TSalesforceInputProperties) sfInputDef.createRuntimeProperties();
        sfInputProps.copyValuesFrom(props);
        sfInputProps.manualQuery.setValue(false);
        sfInputProps.module.main.schema.setValue(SCHEMA_QUERY_ACCOUNT);
        sfInputProps.queryMode.setValue(TSalesforceInputProperties.QueryMode.Bulk);
        sfInputProps.condition.setValue("BillingPostalCode = '" + random + "'");
        sfInputProps.returnNullValue.setValue(returnNullForEmpty);

        List<IndexedRecord> inpuRecords = readRows(sfInputProps);
        for (IndexedRecord record : inpuRecords) {
            if (returnNullForEmpty) {
                assertNull(record.get(3));
            } else {
                assertNotNull(record.get(3));
            }
            assertNull(record.get(5));
            assertNull(record.get(6));
            if (returnNullForEmpty) {
                assertNull(record.get(7));
            } else {
                assertNotNull(record.get(7));
            }
        }
    }

    protected List<IndexedRecord> checkRows(SalesforceConnectionModuleProperties props, String soql, int nbLine,
            boolean bulkQuery,boolean includeDeleted) throws IOException {
        TSalesforceInputProperties inputProps = (TSalesforceInputProperties) new TSalesforceInputProperties("bar").init();
        inputProps.connection = props.connection;
        inputProps.module = props.module;
        inputProps.batchSize.setValue(200);
        inputProps.includeDeleted.setValue(includeDeleted);
        if (bulkQuery) {
            inputProps.queryMode.setValue(TSalesforceInputProperties.QueryMode.Bulk);
        } else {
            inputProps.queryMode.setValue(TSalesforceInputProperties.QueryMode.Query);
        }
        inputProps.manualQuery.setValue(true);
        inputProps.query.setValue(soql);
        List<IndexedRecord> inputRows = readRows(inputProps);
        SalesforceReader<IndexedRecord> reader = (SalesforceReader) createBoundedReader(inputProps);
        boolean hasRecord = reader.start();
        List<IndexedRecord> rows = new ArrayList<>();
        while (hasRecord) {
            org.apache.avro.generic.IndexedRecord unenforced = reader.getCurrent();
            rows.add(unenforced);
            hasRecord = reader.advance();
        }
        Map<String, Object> result = reader.getReturnValues();
        Object totalCount = result.get(ComponentDefinition.RETURN_TOTAL_RECORD_COUNT);
        assertNotNull(totalCount);
        assertThat((int) totalCount, is(nbLine));
        return inputRows;
    }
    protected List<IndexedRecord> checkRows(SalesforceConnectionModuleProperties props, String soql, int nbLine,
                                            boolean bulkQuery) throws IOException {
        return checkRows(props,soql,nbLine,bulkQuery,false);
    }
}
