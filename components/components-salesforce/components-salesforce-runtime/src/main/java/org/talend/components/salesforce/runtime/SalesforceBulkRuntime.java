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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.components.api.exception.ComponentException;
import org.talend.components.salesforce.SalesforceBulkProperties.Concurrency;
import org.talend.components.salesforce.SalesforceOutputProperties.OutputAction;
import org.talend.components.salesforce.common.SalesforceErrorCodes;
import org.talend.components.salesforce.runtime.common.SalesforceRuntimeCommon;
import org.talend.components.salesforce.tsalesforceinput.TSalesforceInputProperties;
import org.talend.daikon.exception.ExceptionContext;
import org.talend.daikon.exception.TalendRuntimeException;
import org.talend.daikon.exception.error.DefaultErrorCode;
import org.talend.daikon.i18n.GlobalI18N;
import org.talend.daikon.i18n.I18nMessages;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sforce.async.AsyncApiException;
import com.sforce.async.AsyncExceptionCode;
import com.sforce.async.BatchInfo;
import com.sforce.async.BatchInfoList;
import com.sforce.async.BatchResult;
import com.sforce.async.BatchStateEnum;
import com.sforce.async.BulkConnection;
import com.sforce.async.CSVReader;
import com.sforce.async.ConcurrencyMode;
import com.sforce.async.ContentType;
import com.sforce.async.JobInfo;
import com.sforce.async.JobStateEnum;
import com.sforce.async.OperationEnum;
import com.sforce.async.QueryResultList;
import com.sforce.async.Result;
import com.sforce.ws.ConnectionException;

/**
 * This contains process a set of records by creating a job that contains one or more batches. The job specifies which
 * object is being processed and what type of action is being used (query, insert, upsert, update, or delete).
 */

public class SalesforceBulkRuntime {

    private final Logger LOGGER = LoggerFactory.getLogger(SalesforceBulkRuntime.class.getName());

    private final I18nMessages MESSAGES =
            GlobalI18N.getI18nMessageProvider().getI18nMessages(SalesforceBulkRuntime.class);

    private final String FILE_ENCODING = "UTF-8";

    private String sObjectType;

    private OperationEnum operation;

    private String externalIdFieldName;

    private ContentType contentType;

    private String bulkFileName;

    private int maxBytesPerBatch;

    private int maxRowsPerBatch;

    private List<BatchInfo> batchInfoList;

    private BufferedReader br;

    private JobInfo job;

    private com.talend.csv.CSVReader baseFileReader;

    private List<String> baseFileHeader;

    private BulkConnection bulkConnection;

    private ConcurrencyMode concurrencyMode = null;

    private Iterator<String> queryResultIDs = null;

    private long awaitTime = 10000L;

    private boolean safetySwitch = true;

    private int chunkSize;

    private String parentObject;

    private int chunkSleepTime;

    private long jobTimeOut;

    private static final String PK_CHUNKING_HEADER_NAME = "Sforce-Enable-PKChunking";

    private static final String CHUNK_SIZE_PROPERTY_NAME = "chunkSize=";

    private static final String PARENT_OBJECT_PROPERTY_NAME = "parent=";

    private static final int MAX_BATCH_EXECUTION_TIME = 600 * 1000;

    public SalesforceBulkRuntime(BulkConnection bulkConnection) throws IOException {
        this.bulkConnection = bulkConnection;
        if (this.bulkConnection == null) {
            throw new RuntimeException(
                    "Please check \"Bulk Connection\" checkbox in the setting of the referenced tSalesforceConnection.");
        }
    }

    public BulkConnection getBulkConnection() {
        return bulkConnection;
    }

    /**
     * Sets up chunk size and chunk sleep time for big chunks.
     *
     * @param properties - Salesforce input properties.
     */
    public void setChunkProperties(TSalesforceInputProperties properties) {
        this.chunkSize = properties.chunkSize.getValue() > TSalesforceInputProperties.MAX_CHUNK_SIZE
                ? TSalesforceInputProperties.MAX_CHUNK_SIZE
                : properties.chunkSize.getValue() <= 0 ? TSalesforceInputProperties.DEFAULT_CHUNK_SIZE
                : properties.chunkSize.getValue();
        this.chunkSleepTime = properties.chunkSleepTime.getValue() > 0 ? properties.chunkSleepTime.getValue() * 1000
                : TSalesforceInputProperties.DEFAULT_CHUNK_SLEEP_TIME * 1000;

        this.parentObject = properties.specifyParent.getValue() ? properties.parentObject.getValue() : "";
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkSleepTime() {
        return chunkSleepTime;
    }

    public void setSafetySwitch(boolean safetySwitch) {
        this.safetySwitch = safetySwitch;
    }

    /**
     * Set the global timeout of the job.
     *
     * @param properties - Salesforce input properties.
     */
    public void setJobTimeout(TSalesforceInputProperties properties) {
        Integer timeout = properties.jobTimeOut.getValue();
        if(timeout == null){
            timeout = TSalesforceInputProperties.DEFAULT_JOB_TIME_OUT;
        }
        this.jobTimeOut = timeout * 1000; // from seconds to milliseconds
    }

    private void setBulkOperation(String sObjectType, OutputAction userOperation, boolean hardDelete, String externalIdFieldName,
            String contentTypeStr, String bulkFileName, int maxBytes, int maxRows) {
        this.sObjectType = sObjectType;
        switch (userOperation) {
        case INSERT:
            operation = OperationEnum.insert;
            break;
        case UPDATE:
            operation = OperationEnum.update;
            break;
        case UPSERT:
            operation = OperationEnum.upsert;
            break;
        case DELETE:
            if(hardDelete){
                operation = OperationEnum.hardDelete;
            }else{
                operation = OperationEnum.delete;
            }
            break;

        default:
            operation = OperationEnum.insert;
            break;
        }
        this.externalIdFieldName = externalIdFieldName;

        contentType = ContentType.valueOf(contentTypeStr);
        this.bulkFileName = bulkFileName;

        int sforceMaxBytes = 10 * 1024 * 1024;
        int sforceMaxRows = 10000;
        maxBytesPerBatch = (maxBytes > sforceMaxBytes) ? sforceMaxBytes : maxBytes;
        maxRowsPerBatch = (maxRows > sforceMaxRows) ? sforceMaxRows : maxRows;
    }

    public void executeBulk(String sObjectType, OutputAction userOperation, boolean hardDelete, String externalIdFieldName, String contentTypeStr,
            String bulkFileName, int maxBytes, int maxRows) throws AsyncApiException, ConnectionException, IOException {
        setBulkOperation(sObjectType, userOperation, hardDelete, externalIdFieldName, contentTypeStr, bulkFileName, maxBytes, maxRows);
        job = createJob();
        if("JSON".equals(contentTypeStr)){
            batchInfoList =  createBatchesFromJSONFile();
            closeJob();
            awaitCompletion();
            prepareJsonLog();
        }else {
            batchInfoList =  createBatchesFromCSVFile();
            closeJob();
            awaitCompletion();
            prepareCSVLog();
        }

    }

    private void prepareCSVLog() throws IOException {
        br = new BufferedReader(new InputStreamReader(new FileInputStream(bulkFileName), FILE_ENCODING));
        baseFileReader = new com.talend.csv.CSVReader(br, ',');
        baseFileReader.setSafetySwitch(safetySwitch);
        if (baseFileReader.readNext()) {
            baseFileHeader = Arrays.asList(baseFileReader.getValues());
        }
    }

    JsonParser jsonParserLog;
    RandomAccessFile readLog;

    private void prepareJsonLog() throws IOException{
        JsonFactory jsonFactory = new JsonFactory();
        jsonParserLog = jsonFactory.createParser(new FileInputStream(bulkFileName));
        readLog = new RandomAccessFile(bulkFileName,"r");
    }

    public void setConcurrencyMode(Concurrency mode) {
        switch (mode) {
        case Parallel:
            concurrencyMode = ConcurrencyMode.Parallel;
            break;
        case Serial:
            concurrencyMode = ConcurrencyMode.Serial;
            break;

        default:
            break;
        }
    }

    public ConcurrencyMode getConcurrencyMode() {
        return concurrencyMode;
    }

    /**
     * Create a new job using the Bulk API.
     *
     * @return The JobInfo for the new job.
     * @throws AsyncApiException
     * @throws ConnectionException
     */
    private JobInfo createJob() throws AsyncApiException, ConnectionException {
        JobInfo job = new JobInfo();
        if (concurrencyMode != null) {
            job.setConcurrencyMode(concurrencyMode);
        }
        job.setObject(sObjectType);
        job.setOperation(operation);
        if (OperationEnum.upsert.equals(operation)) {
            job.setExternalIdFieldName(externalIdFieldName);
        }
        job.setContentType(contentType);
        job = createJob(job);
        return job;
    }

    private int countQuotes(String value) {
        if (value == null || "".equals(value)) {
            return 0;
        } else {
            char c = '\"';
            int num = 0;
            char[] chars = value.toCharArray();
            for (char d : chars) {
                if (c == d) {
                    num++;
                }
            }
            return num;
        }
    }

    /**
     * Create and upload batches using a CSV file. The file into the appropriate size batch files.
     *
     * @return
     * @throws IOException
     * @throws AsyncApiException
     * @throws ConnectionException
     */
    private List<BatchInfo> createBatchesFromCSVFile() throws IOException, AsyncApiException, ConnectionException {
        List<BatchInfo> batchInfos = new ArrayList<BatchInfo>();
        BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(bulkFileName), FILE_ENCODING));
        // read the CSV header row
        byte[] headerBytes = (rdr.readLine() + "\n").getBytes("UTF-8");
        int headerBytesLength = headerBytes.length;
        File tmpFile = File.createTempFile("sforceBulkAPI", ".csv");
        // Split the CSV file into multiple batches
        try {
            FileOutputStream tmpOut = new FileOutputStream(tmpFile);
            int currentBytes = 0;
            int currentLines = 0;
            String nextLine;
            boolean needStart = true;
            boolean needEnds = true;
            while ((nextLine = rdr.readLine()) != null) {
                int num = countQuotes(nextLine);
                // nextLine is header or footer of the record
                if (num % 2 == 1) {
                    if (!needStart) {
                        needEnds = false;
                    } else {
                        needStart = false;
                    }
                } else {
                    // nextLine is a whole record or middle of the record
                    if (needEnds && needStart) {
                        needEnds = false;
                        needStart = false;
                    }
                }

                byte[] bytes = (nextLine + "\n").getBytes("UTF-8");

                // Create a new batch when our batch size limit is reached
                if (currentBytes + bytes.length > maxBytesPerBatch || currentLines > maxRowsPerBatch) {
                    createBatch(tmpOut, tmpFile, batchInfos);
                    currentBytes = 0;
                    currentLines = 0;
                }
                if (currentBytes == 0) {
                    tmpOut = new FileOutputStream(tmpFile);
                    tmpOut.write(headerBytes);
                    currentBytes = headerBytesLength;
                    currentLines = 1;
                }
                tmpOut.write(bytes);
                currentBytes += bytes.length;
                if (!needStart && !needEnds) {
                    currentLines++;
                    needStart = true;
                    needEnds = true;
                }
            }
            // Finished processing all rows
            // Create a final batch for any remaining data
            rdr.close();
            if (currentLines > 1) {
                createBatch(tmpOut, tmpFile, batchInfos);
            }
        } finally {
            tmpFile.delete();
        }
        return batchInfos;
    }



    /**
     * Create and upload batches using a Json file. The file into the appropriate size batch files.
     *
     * @return
     * @throws IOException
     * @throws AsyncApiException
     * @throws ConnectionException
     */
    private List<BatchInfo> createBatchesFromJSONFile() throws IOException, AsyncApiException, ConnectionException {
        List<BatchInfo> batchInfos = new ArrayList<BatchInfo>();
        long startTime = System.currentTimeMillis();
        JsonFactory jsonFactory = new JsonFactory();
        int bucket = 0;
        int recordCount = 0;
        long recordStart = 0l;//offset of last record start
        long recodeEnd = 0l;//offset of last record end
        long offset = 0l;//batch offset
        try (JsonParser jsonParser = jsonFactory.createParser(new FileInputStream(bulkFileName))) {

            if (JsonToken.START_ARRAY != jsonParser.nextToken()) {
                throw new ComponentException(new IOException(MESSAGES.getMessage("error.bulk.jsonArray")));
            }

            while (jsonParser.nextToken() != null) {
                switch (jsonParser.currentToken()) {
                case START_OBJECT:
                    if (bucket == 0) {
                        recordStart = jsonParser.getCurrentLocation().getByteOffset() - 1;
                        if (recordCount == 0) {
                            offset = recordStart;
                        }
                    }
                    bucket++;
                    break;
                case END_OBJECT:
                    if (--bucket == 0) {
                        recordCount++;
                        final long current = jsonParser.getCurrentLocation().getByteOffset();

                        if (current - offset > maxBytesPerBatch) {
                            LOGGER.debug("maxBytes {} reached.", maxBytesPerBatch);
                            createJsonBatch(offset, recodeEnd - offset, batchInfos);
                            recordCount = 1;
                            offset = recordStart;
                        }
                        recodeEnd = current;

                        if (recordCount == maxRowsPerBatch) {
                            LOGGER.debug("maxRecordAmount {} reached.", maxRowsPerBatch);
                            createJsonBatch(offset, recodeEnd - offset, batchInfos);
                            recordCount = 0;
                            recordStart = 0;
                        }

                    }
                    break;
                }
            }
        }
        LOGGER.debug("End of File reached");
        LOGGER.debug("RecordCount: {}", recordCount);
        if(recordCount>0) {
            createJsonBatch(offset, recodeEnd - offset, batchInfos);
        }

        long endTime = System.currentTimeMillis();

        LOGGER.debug("Last time: {} milliseconds", (endTime - startTime));

        return batchInfos;
    }

    private void createJsonBatch(long offset,long length,List<BatchInfo> batchInfos)
            throws IOException, AsyncApiException, ConnectionException {

        File tmpFile = File.createTempFile("sforceBulkAPI", ".json");
        LOGGER.debug("Temp File: " + tmpFile +" Created with offset " + offset + " ,length: " + length);

        try (RandomAccessFile read = new RandomAccessFile(bulkFileName,"r");
                FileOutputStream fileOutputStream = new FileOutputStream(tmpFile)){
            read.seek(offset);
            fileOutputStream.write(91);//write ARRAY_START
            byte[] buffer = new byte[8192];
            for (;length > 0;length -= 8192){
                if(length >= 8192){
                    read.read(buffer);
                    fileOutputStream.write(buffer);
                }else {
                    int lastPart = Long.valueOf(length).intValue();
                    read.read(buffer,0, lastPart);
                    fileOutputStream.write(buffer,0, lastPart);
                }
            }
            fileOutputStream.write(93);// write ARRAY_END
            fileOutputStream.flush();
        }

        try (FileInputStream tmpInputStream = new FileInputStream(tmpFile)){
            BatchInfo batchInfo = createBatchFromStream(job, tmpInputStream);
            LOGGER.debug("Batch Infor: " +batchInfo);
            batchInfos.add(batchInfo);
        } finally {
            tmpFile.delete();
        }

    }

    /**
     * Create a batch by uploading the contents of the file. This closes the output stream.
     *
     * @param tmpOut The output stream used to write the CSV data for a single batch.
     * @param tmpFile The file associated with the above stream.
     * @param batchInfos The batch info for the newly created batch is added to this list.
     *
     * @throws IOException
     * @throws AsyncApiException
     * @throws ConnectionException
     */
    private void createBatch(FileOutputStream tmpOut, File tmpFile, List<BatchInfo> batchInfos)
            throws IOException, AsyncApiException, ConnectionException {
        tmpOut.flush();
        tmpOut.close();
        try(FileInputStream tmpInputStream = new FileInputStream(tmpFile)) {
            BatchInfo batchInfo = createBatchFromStream(job, tmpInputStream);
            batchInfos.add(batchInfo);
        }
    }

    /**
     * Close the job
     *
     * @throws AsyncApiException
     * @throws ConnectionException
     */
    public void closeJob() throws AsyncApiException, ConnectionException {
        if(job == null){
            return;
        }
        JobInfo closeJob = new JobInfo();
        closeJob.setId(job.getId());
        closeJob.setState(JobStateEnum.Closed);
        try {
            bulkConnection.updateJob(closeJob);
        } catch (AsyncApiException sfException) {
            if (AsyncExceptionCode.InvalidSessionId.equals(sfException.getExceptionCode())) {
                SalesforceRuntimeCommon.renewSession(bulkConnection.getConfig());
                closeJob();
            } else if (AsyncExceptionCode.InvalidJobState.equals(sfException.getExceptionCode())) {
                // Job is already closed on Salesforce side. We don't need to close it again.
                return;
            }
            throw sfException;
        }
    }

    public void setAwaitTime(long awaitTime) {
        this.awaitTime = awaitTime;
    }

    /**
     * Wait for a job to complete by polling the Bulk API.
     *
     * @throws AsyncApiException
     * @throws ConnectionException
     */
    private void awaitCompletion() throws AsyncApiException, ConnectionException {
        long sleepTime = 0L;
        Set<String> incomplete = new HashSet<String>();
        for (BatchInfo bi : batchInfoList) {
            incomplete.add(bi.getId());
        }
        while (!incomplete.isEmpty()) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
            }
            sleepTime = awaitTime;
            BatchInfo[] statusList = getBatchInfoList(job.getId()).getBatchInfo();
            for (BatchInfo b : statusList) {
                if (b.getState() == BatchStateEnum.Completed || b.getState() == BatchStateEnum.Failed) {
                    incomplete.remove(b.getId());
                }
            }
        }
    }

    /**
     * Get result from the reader
     *
     * @return
     * @throws IOException
     */
    private BulkResult getBaseFileRow() throws IOException {
        BulkResult dataInfo = new BulkResult();
        if (baseFileReader.readNext()) {
            List<String> row = Arrays.asList(baseFileReader.getValues());
            for (int i = 0; i < row.size(); i++) {
                dataInfo.setValue(baseFileHeader.get(i), row.get(i));
            }
        }
        return dataInfo;
    }

    private BulkResult getJsonBaseFileRow() throws IOException {
        final  BulkResult result = new BulkResult();
        ObjectMapper record = new ObjectMapper();
        String nextRecord = getNextRecord();
        JsonNode jsonNode = record.readTree(nextRecord);
        Iterator<String> stringIterator = jsonNode.fieldNames();
        stringIterator.forEachRemaining(e->result.setValue(e,jsonNode.get(e).asText()));
        return result;
    }

    long recodeEnd = 0l;//offset of last record end
    long offset = 0l;//batch offset

    private String getNextRecord() throws IOException {
        int bucket = 0;
        while(jsonParserLog.nextToken() != null){
            switch (jsonParserLog.currentToken()){
            case START_OBJECT:

                if(bucket==0){
                    offset = jsonParserLog.getCurrentLocation().getByteOffset()-1;
                }
                bucket++;
                break;
            case END_OBJECT:
                if(--bucket==0){
                    recodeEnd = jsonParserLog.getCurrentLocation().getByteOffset();
                    long length = recodeEnd - offset;
                    byte[] buffer = new byte[8192];
                    StringBuilder content = new StringBuilder();
                    readLog.seek(offset);
                    for (;length > 0;length -= 8192) {
                        if (length >= 8192) {
                            readLog.read(buffer);
                        } else {
                            int lastPart = Long.valueOf(length).intValue();
                            readLog.read(buffer, 0, lastPart);
                        }
                        content.append(new String(buffer));
                    }
                    return content.toString();
                }
                break;
            }
        }
        return "";

    }

    /**
     * Gets the results of the operation and checks for errors.
     *
     * @param batchNum
     * @return
     * @throws AsyncApiException
     * @throws IOException
     * @throws ConnectionException
     */
    public List<BulkResult> getBatchLog(int batchNum) throws AsyncApiException, IOException, ConnectionException {

        return getBatchLog(batchNum, null);

    }

    protected List<BulkResult> getBatchLog(int batchNum, String upsertKeyName)
            throws AsyncApiException, IOException, ConnectionException {
        if(ContentType.JSON.equals(contentType)){
            return getJSONBatchLog(batchNum,upsertKeyName);
        }else{
            return getCSVBatchLog(batchNum,upsertKeyName);
        }
    }

    public List<BulkResult> getJSONBatchLog(int batchNum, String upsertKeyName)
            throws AsyncApiException, IOException, ConnectionException {
        BatchInfo b = batchInfoList.get(batchNum);
        BatchResult batchResult = getBatchResult(job.getId(), b.getId());
        Result[] results = batchResult.getResult();
        List<BulkResult> collect = new ArrayList<>();

        for(Result r : results){
            BulkResult resultInfo = new BulkResult();
            resultInfo.copyValues(getJsonBaseFileRow());
            resultInfo.setValue("salesforce_created", String.valueOf(r.getCreated()));
            resultInfo.setValue("salesforce_id", r.getId());
            resultInfo.setValue("Success", String.valueOf(r.getSuccess()));
            resultInfo.setValue("Error", Arrays.toString(r.getErrors()));
            collect.add(resultInfo);
        }
        return collect;

    }

    public List<BulkResult> getCSVBatchLog(int batchNum, String upsertKeyName)
            throws AsyncApiException, IOException, ConnectionException {
        // batchInfoList was populated when batches were created and submitted
        List<BulkResult> resultInfoList = new ArrayList<BulkResult>();
        BulkResult resultInfo;
        BatchInfo b = batchInfoList.get(batchNum);
        CSVReader rdr = new CSVReader(getBatchResultStream(job.getId(), b.getId()));

        List<String> resultHeader = rdr.nextRecord();
        int resultCols = resultHeader.size();
        List<String> row;
        while ((row = rdr.nextRecord()) != null) {
            resultInfo = new BulkResult();
            resultInfo.copyValues(getBaseFileRow());
            // save upsert key column name and value in result info
            if (upsertKeyName != null && resultInfo.containField(upsertKeyName)) {
                resultInfo.setValue("UpsertColumnValue", resultInfo.getValue(upsertKeyName));
            }
            for (int i = 0; i < resultCols; i++) {
                String header = resultHeader.get(i);
                Object resultColumnValue = row.get(i);
                if (resultColumnValue != null) {
                    resultInfo.setValue(header, resultColumnValue);
                }

                if ("Created".equals(header)) {
                    resultInfo.setValue("salesforce_created", row.get(i));
                    continue;
                }
                if ("Id".equals(header)) {
                    resultInfo.setValue("salesforce_id", row.get(i));
                    continue;
                }
            }
            resultInfoList.add(resultInfo);
        }

        return resultInfoList;
    }

    public int getBatchCount() {
        return batchInfoList.size();
    }

    /**
     * Creates and executes job for bulk query. Job must be finished in 2 minutes on Salesforce side.<br/>
     * From Salesforce documentation two scenarios are possible here:
     * <ul>
     * <li>simple bulk query. It should have status - {@link BatchStateEnum#Completed}.</li>
     * <li>primary key chunking bulk query. It should return first batch info with status - {@link BatchStateEnum#NotProcessed}.<br/>
     * Other batch info's should have status - {@link BatchStateEnum#Completed}</li>
     * </ul>
     *
     * @param moduleName - input module name.
     * @param queryStatement - to be executed.
     * @param includeDeleted - whether include deleted records.
     * @throws AsyncApiException
     * @throws InterruptedException
     * @throws ConnectionException
     */
    public void doBulkQuery(String moduleName, String queryStatement, boolean includeDeleted)
            throws AsyncApiException, InterruptedException, ConnectionException {
        job = new JobInfo();
        job.setObject(moduleName);
        if(includeDeleted){
            job.setOperation(OperationEnum.queryAll);
        }else {
            job.setOperation(OperationEnum.query);
        }
        if (concurrencyMode != null) {
            job.setConcurrencyMode(concurrencyMode);
        }
        job.setContentType(ContentType.CSV);
        job = createJob(job);
        if (job.getId() == null) { // job creation failed
            throw new ComponentException(new DefaultErrorCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "failedBatch"),
                    ExceptionContext.build().put("failedBatch", job));
        }

        ByteArrayInputStream bout = new ByteArrayInputStream(queryStatement.getBytes());
        BatchInfo info = createBatchFromStream(job, bout);
        int secToWait = 1;
        int tryCount = 0;
        while (true) {
            LOGGER.debug("Awaiting " + secToWait + " seconds for results ...\n" + info);
            Thread.sleep(secToWait * 1000);
            info = getBatchInfo(job.getId(), info.getId());

            if (info.getState() == BatchStateEnum.Completed
                    || (BatchStateEnum.NotProcessed == info.getState() && 0 < chunkSize)) {
                break;
            } else if (info.getState() == BatchStateEnum.Failed) {
                throw new ComponentException(new DefaultErrorCode(HttpServletResponse.SC_BAD_REQUEST, "failedBatch"),
                        ExceptionContext.build().put("failedBatch", info));
            }
            tryCount++;
            if (tryCount % 3 == 0 && secToWait < 120) {// after 3 attempt to get the result we multiply the time to wait by 2
                secToWait = secToWait * 2; // if secToWait < 120 : don't increase exponentially, no need to sleep more than 128 seconds
            }

            // The user can specify a global timeout for the job processing to suites some bulk limits :
            // https://developer.salesforce.com/docs/atlas.en-us.api_asynch.meta/api_asynch/asynch_api_concepts_limits.htm
            if(jobTimeOut > 0) { // if 0, timeout is disabled
                long processingTime = System.currentTimeMillis() - job.getCreatedDate().getTimeInMillis();
                if (processingTime > jobTimeOut) {
                    throw new ComponentException(
                            new DefaultErrorCode(HttpServletResponse.SC_REQUEST_TIMEOUT, "failedBatch"),
                            ExceptionContext.build().put("failedBatch", info));
                }
            }
        }

        retrieveResultsOfQuery(info);
    }

    public BulkResultSet getQueryResultSet(String resultId) throws AsyncApiException, IOException, ConnectionException {
        baseFileReader = new com.talend.csv.CSVReader(new BufferedReader(
                new InputStreamReader(getQueryResultStream(job.getId(), batchInfoList.get(0).getId(), resultId), FILE_ENCODING)),
                ',');

        baseFileReader.setSafetySwitch(safetySwitch);
        if (baseFileReader.readNext()) {
            baseFileHeader = Arrays.asList(baseFileReader.getValues());
        }
        return new BulkResultSet(baseFileReader, baseFileHeader);
    }

    protected JobInfo createJob(JobInfo job) throws AsyncApiException, ConnectionException {
        try {
            String pkChunkingHeaderValue = "";
            if (0 != chunkSize) {
                pkChunkingHeaderValue = CHUNK_SIZE_PROPERTY_NAME + chunkSize;
            }
            if (parentObject != null && !parentObject.isEmpty()) {
                if (!pkChunkingHeaderValue.isEmpty()) {
                    pkChunkingHeaderValue += ";";
                }
                pkChunkingHeaderValue += PARENT_OBJECT_PROPERTY_NAME + parentObject;
            }
            if (!pkChunkingHeaderValue.isEmpty()) {
                // Enabling PK chunking by setting header and chunk size.
                bulkConnection.addHeader(PK_CHUNKING_HEADER_NAME, pkChunkingHeaderValue);
            }
            return bulkConnection.createJob(job);
        } catch (AsyncApiException sfException) {
            if (AsyncExceptionCode.InvalidSessionId.equals(sfException.getExceptionCode())) {
                SalesforceRuntimeCommon.renewSession(bulkConnection.getConfig());
                return createJob(job);
            }
            throw sfException;
        } finally {
            if (0 != chunkSize) {
                // Need to disable PK chunking after job was created.
                bulkConnection.addHeader(PK_CHUNKING_HEADER_NAME, Boolean.FALSE.toString());
            }
        }
    }

    protected BatchInfo createBatchFromStream(JobInfo job, InputStream input) throws AsyncApiException, ConnectionException {
        try {
            return bulkConnection.createBatchFromStream(job, input);
        } catch (AsyncApiException sfException) {
            if (AsyncExceptionCode.InvalidSessionId.equals(sfException.getExceptionCode())) {
                SalesforceRuntimeCommon.renewSession(bulkConnection.getConfig());
                return createBatchFromStream(job, input);
            }
            throw sfException;
        }
    }

    protected JobInfo updateJob(JobInfo job) throws AsyncApiException, ConnectionException {
        try {
            return bulkConnection.updateJob(job);
        } catch (AsyncApiException sfException) {
            if (AsyncExceptionCode.InvalidSessionId.equals(sfException.getExceptionCode())) {
                SalesforceRuntimeCommon.renewSession(bulkConnection.getConfig());
                return updateJob(job);
            }
            throw sfException;
        }
    }

    protected BatchInfoList getBatchInfoList(String jobID) throws AsyncApiException, ConnectionException {
        try {
            return bulkConnection.getBatchInfoList(jobID,contentType);
        } catch (AsyncApiException sfException) {
            if (AsyncExceptionCode.InvalidSessionId.equals(sfException.getExceptionCode())) {
                SalesforceRuntimeCommon.renewSession(bulkConnection.getConfig());
                return getBatchInfoList(jobID);
            }
            throw sfException;
        }
    }

    protected InputStream getBatchResultStream(String jobID, String batchID) throws AsyncApiException, ConnectionException {
        try {
            return bulkConnection.getBatchResultStream(jobID, batchID);
        } catch (AsyncApiException sfException) {
            if (AsyncExceptionCode.InvalidSessionId.equals(sfException.getExceptionCode())) {
                SalesforceRuntimeCommon.renewSession(bulkConnection.getConfig());
                return getBatchResultStream(jobID, batchID);
            }
            throw sfException;
        }
    }

    protected BatchResult getBatchResult(String jobID, String batchID) throws AsyncApiException, ConnectionException {
        try {
            return bulkConnection.getBatchResult(jobID, batchID,contentType);
        } catch (AsyncApiException sfException) {
            if (AsyncExceptionCode.InvalidSessionId.equals(sfException.getExceptionCode())) {
                SalesforceRuntimeCommon.renewSession(bulkConnection.getConfig());
                return getBatchResult(jobID, batchID);
            }
            throw sfException;
        }
    }

    protected JobInfo getJobStatus(String jobID) throws AsyncApiException, ConnectionException {
        try {
            return bulkConnection.getJobStatus(jobID);
        } catch (AsyncApiException sfException) {
            if (AsyncExceptionCode.InvalidSessionId.equals(sfException.getExceptionCode())) {
                SalesforceRuntimeCommon.renewSession(bulkConnection.getConfig());
                return getJobStatus(jobID);
            }
            throw sfException;
        }
    }

    protected BatchInfo getBatchInfo(String jobID, String batchID) throws AsyncApiException, ConnectionException {
        try {
            return bulkConnection.getBatchInfo(jobID, batchID);
        } catch (AsyncApiException sfException) {
            if (AsyncExceptionCode.InvalidSessionId.equals(sfException.getExceptionCode())) {
                SalesforceRuntimeCommon.renewSession(bulkConnection.getConfig());
                return getBatchInfo(jobID, batchID);
            }
            throw sfException;
        }
    }

    public void close() throws IOException {
        closeResources(br,readLog,jsonParserLog);
    }

    private void closeResources(Closeable... resources) throws IOException {

        List<IOException> exceptions = new ArrayList<>();
        for (Closeable closeable : resources) {
            if (closeable!=null){
                try {
                    closeable.close();
                } catch (IOException e) {
                    exceptions.add(e);
                }
            }
        }
        if(exceptions.size()>0){
            throw exceptions.get(0);
        }

    }

    protected QueryResultList getQueryResultList(String jobID, String batchID) throws AsyncApiException, ConnectionException {
        try {
            return bulkConnection.getQueryResultList(jobID, batchID);
        } catch (AsyncApiException sfException) {
            if (AsyncExceptionCode.InvalidSessionId.equals(sfException.getExceptionCode())) {
                SalesforceRuntimeCommon.renewSession(bulkConnection.getConfig());
                return getQueryResultList(jobID, batchID);
            }
            throw sfException;
        }
    }

    protected InputStream getQueryResultStream(String jobID, String batchID, String resultID)
            throws AsyncApiException, ConnectionException {
        try {
            return bulkConnection.getQueryResultStream(jobID, batchID, resultID);
        } catch (AsyncApiException sfException) {
            if (AsyncExceptionCode.InvalidSessionId.equals(sfException.getExceptionCode())) {
                SalesforceRuntimeCommon.renewSession(bulkConnection.getConfig());
                return getQueryResultStream(jobID, batchID, resultID);
            }
            throw sfException;
        }
    }

    /**
     * Retrieve resultId(-s) from job batches info.
     * Results will be retrieved only from completed batches.
     *
     * When pk chunking is enabled, we need to go through all batches in the job.
     * More information on Salesforce documentation:
     * https://developer.salesforce.com/docs/atlas.en-us.api_asynch.meta/api_asynch/
     * asynch_api_code_curl_walkthrough_pk_chunking.htm
     *
     * If some batches were queued or in progress, we must wait till they completed or failed/notprocessed.
     * Quick instructions for primary key chunking flow may be read here:
     * https://developer.salesforce.com/docs/atlas.en-us.api_asynch.meta/api_asynch/asynch_api_bulk_query_processing.htm
     *
     * @param info - batch info from created job.
     * @throws AsyncApiException
     * @throws ConnectionException
     * @throws InterruptedException
     */
    private void retrieveResultsOfQuery(BatchInfo info) throws AsyncApiException, ConnectionException, InterruptedException {

        if (BatchStateEnum.Completed == info.getState()) {
            QueryResultList list = getQueryResultList(job.getId(), info.getId());
            queryResultIDs = new HashSet<String>(Arrays.asList(list.getResult())).iterator();
            this.batchInfoList = Collections.singletonList(info);
            return;
        }
        BatchInfoList batchInfoList = null;
        Set<String> resultSet = new HashSet<>();
        boolean isInProgress = true;
        while (isInProgress) {
            batchInfoList = getBatchInfoList(job.getId());
            isInProgress = isJobBatchesInProgress(batchInfoList, info);
            if (isInProgress) {
                Thread.sleep(chunkSleepTime);
                long processingTime = System.currentTimeMillis() - job.getCreatedDate().getTimeInMillis();
                if (processingTime > MAX_BATCH_EXECUTION_TIME) {
                    // Break processing and return processed data if any batch was processed.
                    LOGGER.warn(MESSAGES.getMessage("warn.batch.timeout"));
                    break;
                }
            }
        }
        for (BatchInfo batch : batchInfoList.getBatchInfo()) {
            if (batch.getId().equals(info.getId())) {
                continue;
            }
            resultSet.addAll(Arrays.asList(getQueryResultList(job.getId(), batch.getId()).getResult()));
            LOGGER.debug("Finished batch info: " + batch.toString().replaceAll("\n", ","));
        }

        queryResultIDs = resultSet.iterator();
        this.batchInfoList = Arrays.asList(batchInfoList.getBatchInfo());
    }

    /**
     * Checks if job batch infos were processed correctly. Only if all batches were {@link BatchStateEnum#Completed} are acceptable.<br/>
     * If any of batches returns {@link BatchStateEnum#Failed} or {@link BatchStateEnum#NotProcessed} - throws an exception.
     *
     * @param batchInfoList - batch infos related to the specific job.
     * @param info - batch info for query batch.
     * @return true - if job is not processed fully, otherwise - false.
     */
    private boolean isJobBatchesInProgress(BatchInfoList batchInfoList, BatchInfo info) {
        for (BatchInfo batch : batchInfoList.getBatchInfo()) {
            if (batch.getId().equals(info.getId())) {
                continue;
            }

            /*
             * More details about every batch state can be found here:
             * https://developer.salesforce.com/docs/atlas.en-us.api_asynch.meta/api_asynch/asynch_api_batches_interpret_status.htm
             */
            switch (batch.getState()) {
            case Completed:
                break;
            case NotProcessed:
                /* If batch was not processed we should abort further execution.
                 * From official documentation:
                 * The batch won’t be processed. This state is assigned when a job is aborted while the batch is queued.
                 */
            case Failed:
                TalendRuntimeException.build(SalesforceErrorCodes.ERROR_IN_BULK_QUERY_PROCESSING)
                        .put(ExceptionContext.KEY_MESSAGE, batch.getStateMessage()).throwIt();
            case Queued:
            case InProgress:
                return true;
            }
        }
        return false;
    }

    public String nextResultId() {
        String resultId = null;
        if (queryResultIDs != null && queryResultIDs.hasNext()) {
            resultId = queryResultIDs.next();
        }
        return resultId;
    }

    public boolean hasNextResultId() {
        return queryResultIDs != null && queryResultIDs.hasNext();
    }

}
