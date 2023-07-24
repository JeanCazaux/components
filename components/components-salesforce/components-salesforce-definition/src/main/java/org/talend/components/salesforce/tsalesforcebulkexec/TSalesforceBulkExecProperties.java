// ============================================================================
//
// Copyright (C) 2006-2017 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.components.salesforce.tsalesforcebulkexec;

import static org.talend.daikon.properties.presentation.Widget.widget;
import static org.talend.daikon.properties.property.PropertyFactory.newBoolean;
import static org.talend.daikon.properties.property.PropertyFactory.newProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.avro.Schema;
import org.talend.components.api.component.ISchemaListener;
import org.talend.components.api.component.PropertyPathConnector;
import org.talend.components.common.avro.AvroTool;
import org.talend.components.salesforce.SalesforceBulkProperties;
import org.talend.components.salesforce.SalesforceConnectionProperties;
import org.talend.components.salesforce.SalesforceOutputProperties;
import org.talend.components.salesforce.tsalesforceconnection.TSalesforceConnectionDefinition;
import org.talend.daikon.avro.SchemaConstants;
import org.talend.daikon.properties.presentation.Form;
import org.talend.daikon.properties.presentation.Widget;
import org.talend.daikon.properties.property.EnumProperty;
import org.talend.daikon.properties.property.Property;

public class TSalesforceBulkExecProperties extends SalesforceOutputProperties {

    public Property<String> bulkFilePath = newProperty("bulkFilePath");

    public SalesforceBulkProperties bulkProperties = new SalesforceBulkProperties("bulkProperties");

    public Property<Boolean> outputUpsertKey = newBoolean("outputUpsertKey");

    public Property<ContentType> contentType = new EnumProperty(ContentType.class, "contentType");

    public TSalesforceBulkExecProperties(String name) {
        super(name);
    }

    @Override
    public void setupLayout() {
        super.setupLayout();
        Form mainForm = getForm(Form.MAIN);
        mainForm.addRow(outputUpsertKey);
        mainForm.addRow(widget(bulkFilePath).setWidgetType(Widget.FILE_WIDGET_TYPE));
        mainForm.addRow(contentType);

        Form advancedForm = getForm(Form.ADVANCED);
        advancedForm.addRow(widget(bulkProperties.getForm(Form.MAIN)));
        advancedForm.addRow(widget(upsertRelationTable).setWidgetType(Widget.TABLE_WIDGET_TYPE));
    }

    @Override
    public void refreshLayout(Form form) {
        super.refreshLayout(form);
        if (Form.MAIN.equals(form.getName())) {
            form.getWidget(outputUpsertKey.getName()).setVisible(OutputAction.UPSERT.equals(outputAction.getValue()));
            form.getWidget(contentType.getName()).setVisible(!isBulkV2());
        }

        if (Form.ADVANCED.equals(form.getName())) {
            form.getChildForm(connection.getName()).getWidget(connection.bulkConnection.getName()).setHidden(true);
            form.getChildForm(connection.getName()).getWidget(connection.httpChunked.getName()).setHidden(true);
            form.getWidget(upsertRelationTable.getName()).setHidden(true);

            Form bulkForm = form.getChildForm(bulkProperties.getName());
            if (bulkForm != null) {
                boolean useBulkApiV2 = isBulkV2();
                bulkForm.getWidget(bulkProperties.rowsToCommit.getName()).setVisible(!useBulkApiV2);
                bulkForm.getWidget(bulkProperties.bytesToCommit.getName()).setVisible(!useBulkApiV2);
                Form main = getForm(Form.MAIN);
                main.getWidget(hardDelete.getName()).setVisible(!useBulkApiV2 && outputAction.getValue().equals(OutputAction.DELETE));
                main.getWidget(contentType.getName()).setVisible(!useBulkApiV2);
                bulkForm.getWidget(bulkProperties.concurrencyMode.getName()).setVisible(!useBulkApiV2);
                bulkForm.getWidget(bulkProperties.columnDelimiter.getName()).setVisible(useBulkApiV2);
                bulkForm.getWidget(bulkProperties.lineEnding.getName()).setVisible(useBulkApiV2);

                form.getChildForm(connection.getName()).getWidget(connection.httpChunked.getName()).setHidden(
                        useBulkApiV2);
            }
        }
    }

    private boolean isBulkV2(){
        Form adv = getForm(Form.ADVANCED);
        Form bulkForm = adv.getChildForm(bulkProperties.getName());
        if(bulkForm != null) {
            SalesforceConnectionProperties sfConn = getEffectiveConnProperties();
            // Note: Avoid issue when job which migrate from old framework, the reference properties is missing
            boolean oauthLogin = (sfConn != null) && SalesforceConnectionProperties.LoginType.OAuth.equals(sfConn.loginType.getStoredValue());
            bulkForm.getWidget(bulkProperties.bulkApiV2.getName()).setVisible(oauthLogin);
            return oauthLogin && bulkProperties.bulkApiV2.getValue();
        }
        return false;
    }

    public void afterLoginType(){
        if(SalesforceConnectionProperties.LoginType.Basic.equals(connection.loginType.getStoredValue())){
            refreshLayout(getForm(Form.MAIN));
        }

    }

    @Override
    public void setupProperties() {
        super.setupProperties();

        connection.bulkConnection.setValue(true);
        connection.httpChunked.setValue(false);
        upsertRelationTable.setUsePolymorphic(true);
        contentType.setValue(ContentType.CSV);

        module.setSchemaListener(new ISchemaListener() {

            @Override
            public void afterSchema() {
                updateOutputSchemas();
                beforeUpsertKeyColumn();
                beforeUpsertRelationTable();
            }

        });
    }

    @Override
    protected boolean isUpsertKeyColumnClosedList() {
        return false;
    }

    private void updateOutputSchemas() {
        final Schema inputSchema = module.main.schema.getValue();

        Schema.Field field = null;

        final List<Schema.Field> additionalMainFields = new ArrayList<Schema.Field>();

        if (OutputAction.UPSERT.equals(outputAction.getValue()) && outputUpsertKey.getValue()
                && inputSchema.getField(upsertKeyColumn.getValue()) == null) {
            field = new Schema.Field("UpsertColumnValue", Schema.create(Schema.Type.STRING), null, (Object) null);
            field.addProp(SchemaConstants.TALEND_IS_LOCKED, "false");
            field.addProp(SchemaConstants.TALEND_FIELD_GENERATED, "true");
            field.addProp(SchemaConstants.TALEND_COLUMN_DB_LENGTH, "255");
            additionalMainFields.add(field);
        }

        field = new Schema.Field("salesforce_id", Schema.create(Schema.Type.STRING), null, (Object) null);
        field.addProp(SchemaConstants.TALEND_IS_LOCKED, "false");
        field.addProp(SchemaConstants.TALEND_FIELD_GENERATED, "true");
        field.addProp(SchemaConstants.TALEND_COLUMN_DB_LENGTH, "255");
        additionalMainFields.add(field);

        field = new Schema.Field("salesforce_created", Schema.create(Schema.Type.STRING), null, (Object) null);
        field.addProp(SchemaConstants.TALEND_IS_LOCKED, "false");
        field.addProp(SchemaConstants.TALEND_FIELD_GENERATED, "true");
        field.addProp(SchemaConstants.TALEND_COLUMN_DB_LENGTH, "255");
        additionalMainFields.add(field);

        Schema mainOutputSchema = newSchema(inputSchema, "output", additionalMainFields);
        schemaFlow.schema.setValue(mainOutputSchema);

        final List<Schema.Field> additionalRejectFields = new ArrayList<Schema.Field>();

        if (OutputAction.UPSERT.equals(outputAction.getValue()) && outputUpsertKey.getValue()
                && inputSchema.getField(upsertKeyColumn.getValue()) == null) {
            field = new Schema.Field("UpsertColumnValue", Schema.create(Schema.Type.STRING), null, (Object) null);
            field.addProp(SchemaConstants.TALEND_IS_LOCKED, "false");
            field.addProp(SchemaConstants.TALEND_FIELD_GENERATED, "true");
            field.addProp(SchemaConstants.TALEND_COLUMN_DB_LENGTH, "255");
            additionalRejectFields.add(field);
        }

        field = new Schema.Field("error", Schema.create(Schema.Type.STRING), null, (Object) null);
        field.addProp(SchemaConstants.TALEND_IS_LOCKED, "false");
        field.addProp(SchemaConstants.TALEND_FIELD_GENERATED, "true");
        field.addProp(SchemaConstants.TALEND_COLUMN_DB_LENGTH, "255");
        additionalRejectFields.add(field);

        Schema rejectSchema = newSchema(inputSchema, "rejectOutput", additionalRejectFields);
        schemaReject.schema.setValue(rejectSchema);
    }

    private Schema newSchema(Schema metadataSchema, String newSchemaName, List<Schema.Field> moreFields) {
        Schema newSchema = Schema.createRecord(newSchemaName, metadataSchema.getDoc(), metadataSchema.getNamespace(),
                metadataSchema.isError());

        List<Schema.Field> copyFieldList = new ArrayList<>();
        for (Schema.Field se : metadataSchema.getFields()) {
            Schema.Field field = AvroTool.cloneAvroFieldWithOrder(se);
            for (Map.Entry<String, Object> entry : se.getObjectProps().entrySet()) {
                field.addProp(entry.getKey(), entry.getValue());
            }
            copyFieldList.add(field);
        }

        copyFieldList.addAll(moreFields);

        newSchema.setFields(copyFieldList);
        for (Map.Entry<String, Object> entry : metadataSchema.getObjectProps().entrySet()) {
            newSchema.addProp(entry.getKey(), entry.getValue());
        }

        return newSchema;
    }

    @Override
    protected Set<PropertyPathConnector> getAllSchemaPropertiesConnectors(boolean isOutputConnection) {
        if (isOutputConnection) {
            HashSet<PropertyPathConnector> connectors = new HashSet<>();
            connectors.add(FLOW_CONNECTOR);
            connectors.add(REJECT_CONNECTOR);
            return connectors;
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public Set<PropertyPathConnector> getPossibleConnectors(boolean isOutputConnection) {
        if (isOutputConnection) {
            HashSet<PropertyPathConnector> connectors = new HashSet<>();
            connectors.add(FLOW_CONNECTOR);
            connectors.add(REJECT_CONNECTOR);
            return connectors;
        } else {
            return Collections.singleton(MAIN_CONNECTOR);
        }
    }

    /**
     * If use connection from connection component, need return the referenced connection properties
     */
    public SalesforceConnectionProperties getEffectiveConnProperties() {
        if (isUseExistConnection()) {
            return connection.getReferencedConnectionProperties();
        }
        return connection;
    }

    /**
     * Whether use other connection information
     */
    public boolean isUseExistConnection() {
        String refComponentIdValue = connection.getReferencedComponentId();
        return refComponentIdValue != null
                && refComponentIdValue.startsWith(TSalesforceConnectionDefinition.COMPONENT_NAME);
    }

    public void afterOutputUpsertKey() {
        refreshLayout(getForm(Form.MAIN));
        refreshLayout(getForm(Form.ADVANCED));
        updateOutputSchemas();
    }

    public enum ContentType {
        CSV,
        JSON
    }

}
