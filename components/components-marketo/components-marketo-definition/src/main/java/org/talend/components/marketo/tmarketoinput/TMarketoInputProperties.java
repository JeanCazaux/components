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
package org.talend.components.marketo.tmarketoinput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.Schema.Type;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.talend.components.api.component.ISchemaListener;
import org.talend.components.api.component.PropertyPathConnector;
import org.talend.components.common.avro.AvroTool;
import org.talend.components.marketo.MarketoConstants;
import org.talend.components.marketo.MarketoUtils;
import org.talend.components.marketo.helpers.CompoundKeyTable;
import org.talend.components.marketo.helpers.IncludeExcludeTypesTable;
import org.talend.components.marketo.helpers.MarketoColumnMappingsTable;
import org.talend.components.marketo.runtime.MarketoSourceOrSinkRuntime;
import org.talend.components.marketo.runtime.MarketoSourceOrSinkSchemaProvider;
import org.talend.components.marketo.wizard.MarketoComponentWizardBaseProperties;
import org.talend.daikon.i18n.GlobalI18N;
import org.talend.daikon.i18n.I18nMessages;
import org.talend.daikon.properties.PresentationItem;
import org.talend.daikon.properties.ValidationResult;
import org.talend.daikon.properties.ValidationResult.Result;
import org.talend.daikon.properties.ValidationResultMutable;
import org.talend.daikon.properties.presentation.Form;
import org.talend.daikon.properties.presentation.Widget;
import org.talend.daikon.properties.property.Property;
import org.talend.daikon.sandbox.SandboxedInstance;
import org.talend.daikon.serialize.PostDeserializeSetup;
import org.talend.daikon.serialize.migration.SerializeSetVersion;

import static org.slf4j.LoggerFactory.getLogger;
import static org.talend.components.marketo.MarketoComponentDefinition.RUNTIME_SOURCEORSINK_CLASS;
import static org.talend.components.marketo.MarketoComponentDefinition.USE_CURRENT_JVM_PROPS;
import static org.talend.components.marketo.MarketoComponentDefinition.getSandboxedInstance;
import static org.talend.components.marketo.MarketoConstants.DATETIME_PATTERN_PARAM;
import static org.talend.components.marketo.MarketoConstants.REST_API_LIMIT;
import static org.talend.components.marketo.MarketoConstants.getRESTSchemaForGetLeadActivity;
import static org.talend.components.marketo.MarketoConstants.getRESTSchemaForGetLeadOrGetMultipleLeads;
import static org.talend.components.marketo.MarketoConstants.getSOAPSchemaForGetLeadActivity;
import static org.talend.components.marketo.runtime.MarketoSourceOrSinkSchemaProvider.RESOURCE_COMPANY;
import static org.talend.components.marketo.runtime.MarketoSourceOrSinkSchemaProvider.RESOURCE_OPPORTUNITY;
import static org.talend.components.marketo.runtime.MarketoSourceOrSinkSchemaProvider.RESOURCE_OPPORTUNITY_ROLE;
import static org.talend.components.marketo.tmarketoinput.TMarketoInputProperties.LeadSelector.LeadKeySelector;
import static org.talend.components.marketo.wizard.MarketoComponentWizardBaseProperties.CustomObjectAction.describe;
import static org.talend.components.marketo.wizard.MarketoComponentWizardBaseProperties.InputOperation.Company;
import static org.talend.components.marketo.wizard.MarketoComponentWizardBaseProperties.InputOperation.CustomObject;
import static org.talend.components.marketo.wizard.MarketoComponentWizardBaseProperties.InputOperation.Opportunity;
import static org.talend.components.marketo.wizard.MarketoComponentWizardBaseProperties.InputOperation.OpportunityRole;
import static org.talend.components.marketo.wizard.MarketoComponentWizardBaseProperties.InputOperation.getLead;
import static org.talend.components.marketo.wizard.MarketoComponentWizardBaseProperties.InputOperation.getLeadActivity;
import static org.talend.components.marketo.wizard.MarketoComponentWizardBaseProperties.InputOperation.getLeadChanges;
import static org.talend.components.marketo.wizard.MarketoComponentWizardBaseProperties.InputOperation.getMultipleLeads;
import static org.talend.daikon.properties.presentation.Widget.widget;
import static org.talend.daikon.properties.property.PropertyFactory.newBoolean;
import static org.talend.daikon.properties.property.PropertyFactory.newEnum;
import static org.talend.daikon.properties.property.PropertyFactory.newInteger;
import static org.talend.daikon.properties.property.PropertyFactory.newString;

public class TMarketoInputProperties extends MarketoComponentWizardBaseProperties implements SerializeSetVersion {

    public Property<Integer> batchSize = newInteger("batchSize");

    public Property<Boolean> dieOnError = newBoolean("dieOnError");

    private static final Logger LOG = getLogger(TMarketoInputProperties.class);

    private static final I18nMessages messages = GlobalI18N.getI18nMessageProvider()
            .getI18nMessages(TMarketoInputProperties.class);

    public enum LeadSelector {
        LeadKeySelector,
        StaticListSelector,
        LastUpdateAtSelector
    }

    public enum LeadKeyTypeREST {
        id,
        cookie,
        email,
        twitterId,
        facebookId,
        linkedInId,
        sfdcAccountId,
        sfdcContactId,
        sfdcLeadId,
        sfdcLeadOwnerId,
        sfdcOpptyId,
        Custom
    }

    public enum LeadKeyTypeSOAP {

        IDNUM, // The Marketo ID (e.g. 64)
        COOKIE, // The value generated by the Munchkin Javascript.
        EMAIL, // The email address associated with the lead. (e.g. rufus@marketo.com)
        SFDCLEADID, // The lead ID from SalesForce
        LEADOWNEREMAIL, // The Lead Owner Email
        SFDCACCOUNTID, // The Account ID from SalesForce
        SFDCCONTACTID, // The Contact ID from SalesForce
        SFDCLEADOWNERID, // The Lead owner ID from SalesForce
        SFDCOPPTYID, // The Opportunity ID from SalesForce
    }

    public enum ListParam {
        STATIC_LIST_NAME,
        STATIC_LIST_ID
    }

    public enum IncludeExcludeFieldsSOAP {
        VisitWebpage,
        FillOutForm,
        ClickLink,
        RegisterForEvent,
        AttendEvent,
        SendEmail,
        EmailDelivered,
        EmailBounced,
        UnsubscribeEmail,
        OpenEmail,
        ClickEmail,
        NewLead,
        ChangeDataValue,
        LeadAssigned,
        NewSFDCOpprtnty,
        Wait,
        RunSubflow,
        RemoveFromFlow,
        PushLeadToSales,
        CreateTask,
        ConvertLead,
        ChangeScore,
        ChangeOwner,
        AddToList,
        RemoveFromList,
        SFDCActivity,
        EmailBouncedSoft,
        PushLeadUpdatesToSales,
        DeleteLeadFromSales,
        SFDCActivityUpdated,
        SFDCMergeLeads,
        MergeLeads,
        ResolveConflicts,
        AssocWithOpprtntyInSales,
        DissocFromOpprtntyInSales,
        UpdateOpprtntyInSales,
        DeleteLead,
        SendAlert,
        SendSalesEmail,
        OpenSalesEmail,
        ClickSalesEmail,
        AddtoSFDCCampaign,
        RemoveFromSFDCCampaign,
        ChangeStatusInSFDCCampaign,
        ReceiveSalesEmail,
        InterestingMoment,
        RequestCampaign,
        SalesEmailBounced,
        ChangeLeadPartition,
        ChangeRevenueStage,
        ChangeRevenueStageManually,
        ComputeDataValue,
        ChangeStatusInProgression,
        ChangeFieldInProgram,
        EnrichWithDatacom,
        ChangeSegment,
        ComputeSegmentation,
        ResolveRuleset,
        SmartCampaignTest,
        SmartCampaignTestTrigger
    }

    public enum IncludeExcludeFieldsREST {
        VisitWebpage(1),
        FillOutForm(2),
        ClickLink(3),
        SendEmail(6),
        EmailDelivered(7),
        EmailBounced(8),
        UnsubscribeEmail(9),
        OpenEmail(10),
        ClickEmail(11),
        NewLead(12),
        ChangeDataValue(13),
        SyncLeadToSFDC(19),
        ConvertLead(21),
        ChangeScore(22),
        ChangeOwner(23),
        AddToList(24),
        RemoveFromList(25),
        SFDCActivity(26),
        EmailBouncedSoft(27),
        DeleteLeadFromSFDC(29),
        SFDCActivityUpdated(30),
        MergeLeads(32),
        AddToOpportunity(34),
        RemoveFromOpportunity(35),
        UpdateOpportunity(36),
        DeleteLead(37),
        SendAlert(38),
        SendSalesEmail(39),
        OpenSalesEmail(40),
        ClickSalesEmail(41),
        AddToSFDCCampaign(42),
        RemoveFromSFDCCampaign(43),
        ChangeStatusInSFDCCampaign(44),
        ReceiveSalesEmail(45),
        InterestingMoment(46),
        RequestCampaign(47),
        SalesEmailBounced(48),
        ChangeLeadPartition(100),
        ChangeRevenueStage(101),
        ChangeRevenueStageManually(102),
        ChangeStatusInProgression(104),
        EnrichWithDataCom(106),
        ChangeSegment(108),
        CallWebhook(110),
        SentForwardToFriendEmail(111),
        ReceivedForwardToFriendEmail(112),
        AddToNurture(113),
        ChangeNurtureTrack(114),
        ChangeNurtureCadence(115),
        ShareContent(400),
        VoteInPoll(401),
        ClickSharedLink(405);

        public int fieldVal;

        private static Map<Integer, IncludeExcludeFieldsREST> map = new HashMap<Integer, IncludeExcludeFieldsREST>();

        static {
            for (IncludeExcludeFieldsREST fv : IncludeExcludeFieldsREST.values()) {
                map.put(fv.fieldVal, fv);
            }
        }

        IncludeExcludeFieldsREST(final int fv) {
            fieldVal = fv;
        }

        public static IncludeExcludeFieldsREST valueOf(int fv) {
            return map.get(fv);
        }

    }

    // Companies / Opportunities / OpportunityRoles
    public enum StandardAction {
        describe,
        get
    }

    public MarketoColumnMappingsTable mappingInput = new MarketoColumnMappingsTable("mappingInput");

    public Property<LeadSelector> leadSelectorSOAP = newEnum("leadSelectorSOAP", LeadSelector.class).setRequired();

    public Property<LeadSelector> leadSelectorREST = newEnum("leadSelectorREST", LeadSelector.class).setRequired();

    public Property<LeadKeyTypeREST> leadKeyTypeREST = newEnum("leadKeyTypeREST", LeadKeyTypeREST.class);

    public Property<LeadKeyTypeSOAP> leadKeyTypeSOAP = newEnum("leadKeyTypeSOAP", LeadKeyTypeSOAP.class);

    public Property<String> customLeadKeyType = newString("customLeadKeyType");

    public Property<String> leadKeyValue = newString("leadKeyValue");

    public Property<String> leadKeyValues = newString("leadKeyValues");

    public Property<Integer> leadKeysSegmentSize = newInteger("leadKeysSegmentSize");

    public Property<ListParam> listParam = newEnum("listParam", ListParam.class);

    public Property<String> listParamListName = newString("listParamListName");

    public Property<Integer> listParamListId = newInteger("listParamListId");

    public Property<String> fieldList = newString("fieldList");

    public Property<String> oldestCreateDate = newString("oldestCreateDate");

    public Property<String> oldestUpdateDate = newString("oldestUpdateDate");

    public Property<String> latestUpdateDate = newString("latestUpdateDate");

    public Property<String> latestCreateDate = newString("latestCreateDate");

    public Property<String> sinceDateTime = newString("sinceDateTime");

    public Property<Boolean> setIncludeTypes = newBoolean("setIncludeTypes");

    public IncludeExcludeTypesTable includeTypes = new IncludeExcludeTypesTable("includeTypes");

    public Property<Boolean> setExcludeTypes = newBoolean("setExcludeTypes");

    public IncludeExcludeTypesTable excludeTypes = new IncludeExcludeTypesTable("excludeTypes");

    public Property<String> customObjectNames = newString("customObjectNames");

    public Property<String> customObjectFilterType = newString("customObjectFilterType").setRequired();

    public Property<String> customObjectFilterValues = newString("customObjectFilterValues").setRequired();

    public transient PresentationItem fetchCustomObjectSchema = new PresentationItem("fetchCustomObjectSchema", "Fetch schema");

    public Property<Boolean> useCompoundKey = newBoolean("useCompoundKey");

    public CompoundKeyTable compoundKey = new CompoundKeyTable("compoundKey");

    public transient PresentationItem fetchCompoundKey = new PresentationItem("fetchCompoundKey", "Fetch Compound Key");

    public Property<StandardAction> standardAction = newEnum("standardAction", StandardAction.class);

    //
    private static final long serialVersionUID = 3335746787979781L;

    public TMarketoInputProperties(String name) {
        super(name);
    }

    @Override
    protected Set<PropertyPathConnector> getAllSchemaPropertiesConnectors(boolean isOutputConnection) {
        if (isOutputConnection) {
            return Collections.singleton(FLOW_CONNECTOR);
        } else {
            return Collections.singleton(MAIN_CONNECTOR);
        }
    }

    @Override
    public void setupProperties() {
        super.setupProperties();

        //
        batchSize.setValue(REST_API_LIMIT);
        dieOnError.setValue(true);
        //
        inputOperation.setPossibleValues((Object[]) InputOperation.values());
        inputOperation.setValue(getLead);
        leadSelectorSOAP.setPossibleValues((Object[]) LeadSelector.values());
        leadSelectorSOAP.setValue(LeadKeySelector);
        leadSelectorREST.setPossibleValues(LeadKeySelector, LeadSelector.StaticListSelector);
        leadSelectorREST.setValue(LeadKeySelector);
        customLeadKeyType.setValue("");
        setIncludeTypes.setValue(false);
        includeTypes.type.setPossibleValues((Object[]) IncludeExcludeFieldsREST.values());
        setExcludeTypes.setValue(false);
        excludeTypes.type.setPossibleValues((Object[]) IncludeExcludeFieldsREST.values());
        fieldList.setValue("");
        leadKeysSegmentSize.setValue(50);
        sinceDateTime.setValue(DATETIME_PATTERN_PARAM);
        oldestCreateDate.setValue(DATETIME_PATTERN_PARAM);
        latestCreateDate.setValue(DATETIME_PATTERN_PARAM);
        oldestUpdateDate.setValue(DATETIME_PATTERN_PARAM);
        latestUpdateDate.setValue(DATETIME_PATTERN_PARAM);
        //
        // Custom Objects
        //
        customObjectAction.setPossibleValues((Object[]) CustomObjectAction.values());
        customObjectAction.setValue(describe);
        customObjectNames.setValue("");
        customObjectFilterType.setValue("");
        customObjectFilterValues.setValue("");
        useCompoundKey.setValue(false);
        //
        // Opportunities / OpportunityRoles
        //
        standardAction.setPossibleValues((Object[]) StandardAction.values());
        standardAction.setValue(StandardAction.describe);
        //
        schemaInput.schema.setValue(getRESTSchemaForGetLeadOrGetMultipleLeads());
        beforeMappingInput();
        setSchemaListener(new ISchemaListener() {

            @Override
            public void afterSchema() {
                schemaFlow.schema.setValue(schemaInput.schema.getValue());
                beforeMappingInput();
            }
        });
    }

    @Override
    public void setupLayout() {
        super.setupLayout();

        Form mainForm = getForm(Form.MAIN);
        mainForm.addRow(inputOperation);
        // Custom Objects & Opportunities
        mainForm.addColumn(customObjectAction);
        mainForm.addColumn(standardAction);
        mainForm.addRow(customObjectName);
        mainForm.addColumn(Widget.widget(fetchCustomObjectSchema).setWidgetType(Widget.BUTTON_WIDGET_TYPE).setLongRunning(true));
        mainForm.addRow(customObjectNames);
        mainForm.addRow(customObjectFilterType);
        mainForm.addColumn(customObjectFilterValues);
        mainForm.addRow(useCompoundKey);
        mainForm.addRow(widget(compoundKey).setWidgetType(Widget.TABLE_WIDGET_TYPE));
        mainForm.addRow(Widget.widget(fetchCompoundKey).setWidgetType(Widget.BUTTON_WIDGET_TYPE).setLongRunning(true));
        //
        mainForm.addRow(widget(mappingInput).setWidgetType(Widget.TABLE_WIDGET_TYPE));
        // leadSelector
        mainForm.addRow(leadSelectorSOAP);
        mainForm.addRow(leadSelectorREST);
        mainForm.addColumn(leadKeyTypeREST);
        mainForm.addColumn(leadKeyTypeSOAP);
        mainForm.addColumn(customLeadKeyType);
        mainForm.addColumn(leadKeyValue);
        mainForm.addColumn(leadKeyValues);
        //
        mainForm.addRow(listParam);
        mainForm.addColumn(listParamListName);
        mainForm.addColumn(listParamListId);
        //
        mainForm.addRow(oldestCreateDate);
        mainForm.addColumn(latestCreateDate);
        //
        mainForm.addRow(oldestUpdateDate);
        mainForm.addColumn(latestUpdateDate);
        //
        mainForm.addRow(setIncludeTypes);
        mainForm.addRow(widget(includeTypes).setWidgetType(Widget.TABLE_WIDGET_TYPE));
        mainForm.addRow(setExcludeTypes);
        mainForm.addRow(widget(excludeTypes).setWidgetType(Widget.TABLE_WIDGET_TYPE));
        //
        mainForm.addRow(fieldList);
        mainForm.addRow(sinceDateTime);
        //
        mainForm.addRow(batchSize);
        mainForm.addRow(dieOnError);

    }

    @Override
    public void refreshLayout(Form form) {
        super.refreshLayout(form);

        boolean useSOAP = isApiSOAP();
        //
        if (form.getName().equals(Form.MAIN)) {
            // first hide everything
            form.getWidget(leadSelectorSOAP.getName()).setVisible(false);
            form.getWidget(leadSelectorREST.getName()).setVisible(false);
            form.getWidget(leadKeyTypeSOAP.getName()).setVisible(false);
            form.getWidget(leadKeyTypeREST.getName()).setVisible(false);
            form.getWidget(customLeadKeyType.getName()).setVisible(false);
            form.getWidget(leadKeyValue.getName()).setVisible(false);
            form.getWidget(leadKeyValues.getName()).setVisible(false);
            form.getWidget(listParam.getName()).setVisible(false);
            form.getWidget(listParamListName.getName()).setVisible(false);
            form.getWidget(listParamListId.getName()).setVisible(false);
            form.getWidget(oldestUpdateDate.getName()).setVisible(false);
            form.getWidget(latestUpdateDate.getName()).setVisible(false);
            form.getWidget(setIncludeTypes.getName()).setVisible(false);
            form.getWidget(setExcludeTypes.getName()).setVisible(false);
            form.getWidget(includeTypes.getName()).setVisible(false);
            form.getWidget(excludeTypes.getName()).setVisible(false);
            form.getWidget(fieldList.getName()).setVisible(false);
            form.getWidget(sinceDateTime.getName()).setVisible(false);
            form.getWidget(oldestCreateDate.getName()).setVisible(false);
            form.getWidget(latestCreateDate.getName()).setVisible(false);
            form.getWidget(batchSize.getName()).setVisible(false);
            // custom objects
            form.getWidget(customObjectAction.getName()).setVisible(false);
            form.getWidget(customObjectName.getName()).setVisible(false);
            form.getWidget(fetchCustomObjectSchema.getName()).setVisible(false);
            form.getWidget(customObjectNames.getName()).setVisible(false);
            form.getWidget(customObjectFilterType.getName()).setVisible(false);
            form.getWidget(customObjectFilterValues.getName()).setVisible(false);
            form.getWidget(useCompoundKey.getName()).setVisible(false);
            form.getWidget(compoundKey.getName()).setVisible(false);
            form.getWidget(fetchCompoundKey.getName()).setVisible(false);
            //
            form.getWidget(standardAction.getName()).setVisible(false);
            //
            // enable widgets according params
            //
            //
            form.getWidget(mappingInput.getName()).setVisible(true);
            // getLead
            if (inputOperation.getValue().equals(getLead)) {
                form.getWidget(leadKeyValue.getName()).setVisible(true);
                if (useSOAP) {
                    form.getWidget(leadKeyTypeSOAP.getName()).setVisible(true);
                } else {
                    form.getWidget(leadKeyTypeREST.getName()).setVisible(true);
                    if (LeadKeyTypeREST.Custom.equals(leadKeyTypeREST.getValue())) {
                        form.getWidget(customLeadKeyType.getName()).setVisible(true);
                    }
                }
            }
            // getMultipleLeads
            if (inputOperation.getValue().equals(getMultipleLeads)) {
                if (useSOAP) {
                    form.getWidget(leadSelectorSOAP.getName()).setVisible(true);
                    switch (leadSelectorSOAP.getValue()) {
                    case LeadKeySelector:
                        form.getWidget(leadKeyTypeSOAP.getName()).setVisible(true);
                        form.getWidget(leadKeyValues.getName()).setVisible(true);
                        break;
                    case StaticListSelector:
                        form.getWidget(listParam.getName()).setVisible(true);
                        if (ListParam.STATIC_LIST_NAME.equals(listParam.getValue())) {
                            form.getWidget(listParamListName.getName()).setVisible(true);
                        } else {
                            form.getWidget(listParamListId.getName()).setVisible(true);
                        }
                        form.getWidget(batchSize.getName()).setVisible(true);
                        break;
                    case LastUpdateAtSelector:
                        form.getWidget(oldestUpdateDate.getName()).setVisible(true);
                        form.getWidget(latestUpdateDate.getName()).setVisible(true);
                        form.getWidget(batchSize.getName()).setVisible(true);
                        break;
                    }
                } else {
                    form.getWidget(leadSelectorREST.getName()).setVisible(true);
                    switch (leadSelectorREST.getValue()) {
                    case LeadKeySelector:
                        form.getWidget(leadKeyTypeREST.getName()).setVisible(true);
                        if (LeadKeyTypeREST.Custom.equals(leadKeyTypeREST.getValue())) {
                            form.getWidget(customLeadKeyType.getName()).setVisible(true);
                        }
                        form.getWidget(leadKeyValues.getName()).setVisible(true);
                        break;
                    case StaticListSelector:
                        form.getWidget(listParam.getName()).setVisible(true);
                        if (ListParam.STATIC_LIST_NAME.equals(listParam.getValue())) {
                            form.getWidget(listParamListName.getName()).setVisible(true);
                        } else {
                            form.getWidget(listParamListId.getName()).setVisible(true);
                        }
                        break;
                    }
                    form.getWidget(batchSize.getName()).setVisible(true);
                }
            }
            // getLeadActivity
            if (inputOperation.getValue().equals(getLeadActivity)) {
                // first set all in/exclude types visibles
                form.getWidget(setIncludeTypes.getName()).setVisible(true);
                form.getWidget(includeTypes.getName()).setVisible(setIncludeTypes.getValue());
                form.getWidget(setExcludeTypes.getName()).setVisible(true);
                form.getWidget(excludeTypes.getName()).setVisible(setExcludeTypes.getValue());
                if (useSOAP) {
                    form.getWidget(leadKeyTypeSOAP.getName()).setVisible(true);
                    form.getWidget(leadKeyValue.getName()).setVisible(true);
                } else {
                    form.getWidget(sinceDateTime.getName()).setVisible(true);
                    if (setIncludeTypes.getValue()) {
                        setExcludeTypes.setValue(false);
                        form.getWidget(setExcludeTypes.getName()).setVisible(false);
                        form.getWidget(excludeTypes.getName()).setVisible(false);
                    } else if (setExcludeTypes.getValue()) {
                        form.getWidget(setIncludeTypes.getName()).setVisible(false);
                        form.getWidget(includeTypes.getName()).setVisible(false);
                    }
                }
                form.getWidget(batchSize.getName()).setVisible(true);
            }
            // getLeadChanges
            if (inputOperation.getValue().equals(getLeadChanges)) {
                if (useSOAP) {
                    form.getWidget(setIncludeTypes.getName()).setVisible(true);
                    form.getWidget(includeTypes.getName()).setVisible(setIncludeTypes.getValue());
                    form.getWidget(setExcludeTypes.getName()).setVisible(true);
                    form.getWidget(excludeTypes.getName()).setVisible(setExcludeTypes.getValue());
                    form.getWidget(oldestCreateDate.getName()).setVisible(true);
                    form.getWidget(latestCreateDate.getName()).setVisible(true);
                } else {
                    form.getWidget(fieldList.getName()).setVisible(true);
                    form.getWidget(sinceDateTime.getName()).setVisible(true);
                }
                form.getWidget(batchSize.getName()).setVisible(true);
            }
            // Custom Objects
            if (inputOperation.getValue().equals(CustomObject)) {
                form.getWidget(mappingInput.getName()).setVisible(false); // don't need mappings for CO.
                form.getWidget(customObjectAction.getName()).setVisible(true);
                switch (customObjectAction.getValue()) {
                case describe:
                    form.getWidget(customObjectName.getName()).setVisible(true);
                    break;
                case list:
                    form.getWidget(customObjectNames.getName()).setVisible(true);
                    break;
                case get:
                    form.getWidget(customObjectName.getName()).setVisible(true);
                    form.getWidget(fetchCustomObjectSchema.getName()).setVisible(true);
                    form.getWidget(useCompoundKey.getName()).setVisible(true);
                    form.getWidget(customObjectFilterType.getName()).setVisible(!useCompoundKey.getValue());
                    form.getWidget(customObjectFilterValues.getName()).setVisible(!useCompoundKey.getValue());
                    form.getWidget(compoundKey.getName()).setVisible(useCompoundKey.getValue());
                    form.getWidget(fetchCompoundKey.getName()).setVisible(useCompoundKey.getValue());
                    form.getWidget(batchSize.getName()).setVisible(true);
                    break;
                }
            }
            // Companies
            if (Company.equals(inputOperation.getValue())) {
                form.getWidget(mappingInput.getName()).setVisible(false);
                form.getWidget(standardAction.getName()).setVisible(true);
                switch (standardAction.getValue()) {
                case describe:
                    break;
                case get:
                    form.getWidget(fetchCustomObjectSchema.getName()).setVisible(true);
                    form.getWidget(customObjectFilterType.getName()).setVisible(true);
                    form.getWidget(customObjectFilterValues.getName()).setVisible(true);
                    form.getWidget(batchSize.getName()).setVisible(true);
                    break;
                }
            }
            // Opportunities*
            if (Opportunity.equals(inputOperation.getValue()) || OpportunityRole.equals(inputOperation.getValue())) {
                form.getWidget(mappingInput.getName()).setVisible(false);
                form.getWidget(standardAction.getName()).setVisible(true);
                switch (standardAction.getValue()) {
                case describe:
                    break;
                case get:
                    form.getWidget(fetchCustomObjectSchema.getName()).setVisible(true);
                    if (OpportunityRole.equals(inputOperation.getValue())) {
                        form.getWidget(useCompoundKey.getName()).setVisible(true);
                        form.getWidget(fetchCompoundKey.getName()).setVisible(useCompoundKey.getValue());
                        form.getWidget(compoundKey.getName()).setVisible(useCompoundKey.getValue());
                        form.getWidget(customObjectFilterType.getName()).setVisible(!useCompoundKey.getValue());
                        form.getWidget(customObjectFilterValues.getName()).setVisible(!useCompoundKey.getValue());
                    } else {

                        form.getWidget(customObjectFilterType.getName()).setVisible(true);
                        form.getWidget(customObjectFilterValues.getName()).setVisible(true);
                    }
                    form.getWidget(batchSize.getName()).setVisible(true);
                    break;
                }
            }
        }
    }

    public ValidationResult validateInputOperation() {
        if (isApiSOAP()) {
            switch (inputOperation.getValue()) {
            case getLead:
            case getMultipleLeads:
            case getLeadActivity:
            case getLeadChanges:
                return ValidationResult.OK;
            case CustomObject:
            case Company:
            case Opportunity:
            case OpportunityRole:
                ValidationResultMutable vr = new ValidationResultMutable();
                vr.setStatus(Result.ERROR);
                vr.setMessage(messages.getMessage("error.validation.customobjects.nosoap"));
                return vr;
            }
        }
        return ValidationResult.OK;
    }

    public ValidationResult validateFetchCustomObjectSchema() {
        ValidationResultMutable vr = new ValidationResultMutable();
        try (SandboxedInstance sandboxedInstance = getSandboxedInstance(RUNTIME_SOURCEORSINK_CLASS, USE_CURRENT_JVM_PROPS)) {
            MarketoSourceOrSinkRuntime sos = (MarketoSourceOrSinkRuntime) sandboxedInstance.getInstance();
            sos.initialize(null, this);
            ValidationResult vConn = sos.validateConnection(this);
            if (!Result.OK.equals(vConn.getStatus())) {
                return vConn;
            }
            String resource = "";
            try {
                Schema schema = null;
                switch (inputOperation.getValue()) {
                case CustomObject:
                    resource = customObjectName.getValue();
                    schema = ((MarketoSourceOrSinkSchemaProvider) sos).getSchemaForCustomObject(customObjectName.getValue());
                    break;
                case Company:
                    resource = "Company";
                    schema = ((MarketoSourceOrSinkSchemaProvider) sos).getSchemaForCompany();
                    break;
                case Opportunity:
                    resource = "Opportunity";
                    schema = ((MarketoSourceOrSinkSchemaProvider) sos).getSchemaForOpportunity();
                    break;
                case OpportunityRole:
                    resource = "OpportunityRole";
                    schema = ((MarketoSourceOrSinkSchemaProvider) sos).getSchemaForOpportunityRole();
                    break;
                }
                if (schema == null) {
                    vr.setStatus(ValidationResult.Result.ERROR).setMessage(
                            messages.getMessage("error.validation.customobjects.fetchcustomobjectschema", resource, "NULL"));
                    return vr;
                }
                schemaInput.schema.setValue(schema);
                vr.setStatus(ValidationResult.Result.OK);
            } catch (RuntimeException | IOException e) {
                vr.setStatus(ValidationResult.Result.ERROR).setMessage(
                        messages.getMessage("error.validation.customobjects.fetchcustomobjectschema", resource, e.getMessage()));
            }
        }
        return vr;
    }

    public ValidationResult validateFetchCompoundKey() {
        ValidationResultMutable vr = new ValidationResultMutable();
        try (SandboxedInstance sandboxedInstance = getSandboxedInstance(RUNTIME_SOURCEORSINK_CLASS, USE_CURRENT_JVM_PROPS)) {
            MarketoSourceOrSinkRuntime sos = (MarketoSourceOrSinkRuntime) sandboxedInstance.getInstance();
            sos.initialize(null, this);
            ValidationResult vConn = sos.validateConnection(this);
            if (!Result.OK.equals(vConn.getStatus())) {
                return vConn;
            }
            String resource = "";
            try {
                List<String> keys = null;
                switch (inputOperation.getValue()) {
                case CustomObject:
                    resource = customObjectName.getValue();
                    break;
                case Company:
                    resource = RESOURCE_COMPANY;
                    break;
                case Opportunity:
                    resource = RESOURCE_OPPORTUNITY;
                    break;
                case OpportunityRole:
                    resource = RESOURCE_OPPORTUNITY_ROLE;
                    break;
                }
                keys = ((MarketoSourceOrSinkSchemaProvider) sos).getCompoundKeyFields(resource);

                if (keys == null) {
                    vr.setStatus(ValidationResult.Result.ERROR).setMessage(messages
                            .getMessage("error.validation.customobjects.fetchcompoundkey", customObjectName.getValue(), "NULL"));
                    return vr;
                }
                compoundKey.keyName.setValue(keys);
                compoundKey.keyValue.setValue(Arrays.asList(new String[keys.size()]));
                vr.setStatus(ValidationResult.Result.OK);
            } catch (RuntimeException | IOException e) {
                vr.setStatus(ValidationResult.Result.ERROR).setMessage(messages.getMessage(
                        "error.validation.customobjects.fetchcompoundkey", customObjectName.getValue(), e.getMessage()));
            }
        }
        return vr;
    }

    public void beforeInputOperation() {
        if (isApiSOAP()) {
            inputOperation.setPossibleValues(getLead, getMultipleLeads, getLeadActivity, getLeadChanges);
        } else {
            inputOperation.setPossibleValues(InputOperation.values());
        }
    }

    public void beforeMappingInput() {
        List<String> fld = getSchemaFields();
        mappingInput.columnName.setValue(fld);
        // protect mappings...
        if (fld.size() != mappingInput.size()) {
            List<String> mcn = new ArrayList<>();
            for (String t : fld) {
                mcn.add("");
            }
            mappingInput.marketoColumnName.setValue(mcn);
        }
    }

    public void afterInputOperation() {
        updateSchemaRelated();
        refreshLayout(getForm(Form.MAIN));
    }

    public void afterCustomObjectAction() {
        afterInputOperation();
    }

    public void afterStandardAction() {
        afterInputOperation();
    }

    public void afterLeadSelectorSOAP() {
        refreshLayout(getForm(Form.MAIN));
    }

    public void afterLeadSelectorREST() {
        refreshLayout(getForm(Form.MAIN));
    }

    public void afterSetIncludeTypes() {
        refreshLayout(getForm(Form.MAIN));
    }

    public void afterSetExcludeTypes() {
        refreshLayout(getForm(Form.MAIN));
    }

    public void afterFetchCustomObjectSchema() {
        refreshLayout(getForm(Form.MAIN));
    }

    public void afterUseCompoundKey() {
        refreshLayout(getForm(Form.MAIN));
    }

    public void afterFetchCompoundKey() {
        refreshLayout(getForm(Form.MAIN));
    }

    public void afterListParam() {
        refreshLayout(getForm(Form.MAIN));
    }

    public void afterLeadKeyTypeREST() {
        refreshLayout(getForm(Form.MAIN));
    }

    public void updateSchemaRelated() {
        Schema s = null;
        if (isApiSOAP()) {
            switch (inputOperation.getValue()) {
            case getLead:
            case getMultipleLeads:
                s = MarketoConstants.getSOAPSchemaForGetLeadOrGetMultipleLeads();
                break;
            case getLeadActivity:
                s = MarketoConstants.getSOAPSchemaForGetLeadActivity();
                break;
            case getLeadChanges:
                s = MarketoConstants.getSOAPSchemaForGetLeadChanges();
                break;
            }
        } else {
            switch (inputOperation.getValue()) {
            case getLead:
            case getMultipleLeads:
                s = MarketoConstants.getRESTSchemaForGetLeadOrGetMultipleLeads();
                break;
            case getLeadActivity:
                s = MarketoConstants.getRESTSchemaForGetLeadActivity();
                break;
            case getLeadChanges:
                s = MarketoConstants.getRESTSchemaForGetLeadChanges();
                break;
            case CustomObject:
                switch (customObjectAction.getValue()) {
                case describe:
                case list:
                    s = MarketoConstants.getCustomObjectDescribeSchema();
                    break;
                case get:
                    s = MarketoConstants.getCustomObjectRecordSchema();
                    break;
                }
                break;
            case Company:
                switch (standardAction.getValue()) {
                case describe:
                    s = MarketoConstants.getCustomObjectDescribeSchema();
                    break;
                case get:
                    s = MarketoConstants.getCompanySchema();
                    break;
                }
                break;
            case Opportunity:
                switch (standardAction.getValue()) {
                case describe:
                    s = MarketoConstants.getCustomObjectDescribeSchema();
                    break;
                case get:
                    s = MarketoConstants.getOpportunitySchema();
                    break;
                }
                break;
            case OpportunityRole:
                switch (standardAction.getValue()) {
                case describe:
                    s = MarketoConstants.getCustomObjectDescribeSchema();
                    break;
                case get:
                    s = MarketoConstants.getOpportunityRoleSchema();
                    break;
                }
                break;
            }
        }
        schemaInput.schema.setValue(s);
        schemaFlow.schema.setValue(s);
        beforeMappingInput();
    }

    @Override
    public int getVersionNumber() {
        return 3;
    }

    private Field getMigratedField(Field origin, Schema expectedSchema, String expectedDIType) {
        Field expectedField = AvroTool.cloneAvroFieldWithCustomSchemaAndOrder(origin, expectedSchema);
        for (Map.Entry<String, Object> entry : origin.getObjectProps().entrySet()) {
            if ("di.column.talendType".equals(entry.getKey())) {
                expectedField.addProp("di.column.talendType", expectedDIType);
            } else {
                expectedField.addProp(entry.getKey(), entry.getValue());
            }
        }
        return expectedField;
    }

    @Override
    public boolean postDeserialize(int version, PostDeserializeSetup setup, boolean persistent) {
        boolean migrated;
        try {
            migrated = super.postDeserialize(version, setup, persistent);
        } catch (ClassCastException cce) {
            migrated = super.postDeserialize(version, setup, false); // don't initLayout
        }
        checkForInvalidStoredProperties();
        // migrate CustomLookup
        if (isApiREST() && (getMultipleLeads.equals(inputOperation.getValue()) || getLead.equals(inputOperation.getValue()))
                && (LeadKeySelector.equals(leadSelectorREST.getValue()))) {
            String value = getEnumStoredValue(leadKeyTypeREST.getStoredValue());
            boolean correctValue = false;
            for (LeadKeyTypeREST lkt : LeadKeyTypeREST.values()) {
                if (lkt.name().equals(value)) {
                    correctValue = true;
                }
            }
            // since `Custom` was added before, we update the Enum for the latest
            leadKeyTypeREST = newEnum("leadKeyTypeREST", LeadKeyTypeREST.class);
            leadKeyTypeREST.setPossibleValues(LeadKeyTypeREST.class.getEnumConstants());
            if (correctValue) {
                if (value != null) {
                    leadKeyTypeREST.setValue(Enum.valueOf(LeadKeyTypeREST.class, value));
                    leadKeyTypeREST.setStoredValue(Enum.valueOf(LeadKeyTypeREST.class, value));
                }
            } else {
                leadKeyTypeREST.setValue(LeadKeyTypeREST.Custom);
                customLeadKeyType.setValue(value != null ? StringUtils.wrap(value, '"') : "");
                LOG.warn("[postDeserialize] Fixing Custom leadKeyType with {}", customLeadKeyType.getValue());
            }
            migrated = true;
        }
        //
        if (version < this.getVersionNumber()) {
            if (getMultipleLeads.equals(inputOperation.getValue())
                    && ((LeadSelector.StaticListSelector.equals(leadSelectorREST.getValue()) && isApiREST())
                            || (LeadSelector.StaticListSelector.equals(leadSelectorSOAP.getValue()) && isApiSOAP()))
                    && ListParam.STATIC_LIST_ID.equals(listParam.getValue()) && listParamListId.getValue() == null) {
                try {
                    String p = listParamListName.getValue();
                    Integer listid = Integer.parseInt(p);
                    listParamListId.setValue(listid);
                } catch (NumberFormatException e) {
                    LOG.warn("Couldn't migrate ListId : {}", e.getMessage());
                }
                migrated = true;
            }
            //
            if (getLeadActivity.equals(inputOperation.getValue()) || getLeadChanges.equals(inputOperation.getValue())) {
                List<Field> fieldsToMigrate = new ArrayList<>();
                String fieldName;
                Field checkedField;
                Type expectedType;
                String expectedDIType;
                Schema expectedFieldSchema;
                // Id || id aren't correctly mapped to API in 631.
                fieldName = isApiSOAP() ? "Id" : "id";
                checkedField = schemaInput.schema.getValue().getField(fieldName);
                if (checkedField != null) {
                    expectedType = isApiSOAP() ? Type.LONG : Type.INT;
                    expectedDIType = isApiSOAP() ? "id_Long" : "id_Integer";
                    LOG.info("Checking Migration for `{}`'s type: expected is {} and actual is {}.", fieldName, expectedType,
                            MarketoUtils.getFieldType(checkedField));
                    if (!expectedType.equals(MarketoUtils.getFieldType(checkedField))) {
                        expectedFieldSchema = isApiSOAP() ? getSOAPSchemaForGetLeadActivity().getField(fieldName).schema()
                                : getRESTSchemaForGetLeadActivity().getField(fieldName).schema();
                        fieldsToMigrate.add(getMigratedField(checkedField, expectedFieldSchema, expectedDIType));
                    }
                }
                // primaryAttributeValueId isn't mapped correctly in 631 (631: Long; 64+: Integer)
                if (isApiREST()) {
                    fieldName = "primaryAttributeValueId";
                    checkedField = schemaInput.schema.getValue().getField(fieldName);
                    if (checkedField != null) {
                        expectedType = Type.INT;
                        expectedDIType = "id_Integer";
                        LOG.info("Checking Migration for `{}`'s type: expected is {} and actual is {}.", fieldName, expectedType,
                                MarketoUtils.getFieldType(checkedField));
                        if (!expectedType.equals(MarketoUtils.getFieldType(checkedField))) {
                            expectedFieldSchema = getRESTSchemaForGetLeadActivity().getField(fieldName).schema();
                            fieldsToMigrate.add(getMigratedField(checkedField, expectedFieldSchema, expectedDIType));
                        }
                    }
                }
                if (fieldsToMigrate.size() > 0) {
                    Schema correctedSchema = MarketoUtils.modifySchemaFields(schemaInput.schema.getValue(), fieldsToMigrate);
                    schemaInput.schema.setValue(correctedSchema);
                    schemaFlow.schema.setValue(correctedSchema);
                    for (Field f : fieldsToMigrate) {
                        LOG.info("Migrated `{}` to type {}.", f.name(), MarketoUtils.getFieldType(f));
                    }
                }
                migrated = true;
            }
            // manage include/exclude types in REST
            if (isApiREST()) {
                if (setIncludeTypes.getValue()) {
                    includeTypes.type.setValue(getFixedIncludeExcludeList(includeTypes.type.getValue()));
                }
                if (setExcludeTypes.getValue()) {
                    excludeTypes.type.setValue(getFixedIncludeExcludeList(excludeTypes.type.getValue()));
                }
                migrated = true;
            }
        }
        return migrated;
    }

    /*
     * translate int ids to include/exclude strings not translated be studio's migration
     *
     */
    private List<String> getFixedIncludeExcludeList(List<String> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (s.matches("^\\d+$")) {
                list.set(i, IncludeExcludeFieldsREST.valueOf(Integer.parseInt(s)).name());
            }
        }
        return list;
    }

    /*
     * Some jobs were corrupted between 6.4 and 6.5 (Class name changes). This fixes thoses jobs in error with a
     * ClassCastException : LinkedHashMap cannot be cast to Enum.
     */
    private void checkForInvalidStoredProperties() {
        inputOperation = checkForInvalidStoredEnumProperty(inputOperation, InputOperation.class);
        customObjectAction = checkForInvalidStoredEnumProperty(customObjectAction, CustomObjectAction.class);
    }
}
