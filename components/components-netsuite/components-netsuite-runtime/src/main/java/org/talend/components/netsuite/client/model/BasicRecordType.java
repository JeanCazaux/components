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

package org.talend.components.netsuite.client.model;

/**
 * Basic types of NetSuite records.
 */
public enum BasicRecordType {
    CRM_CUSTOM_FIELD("crmCustomField", null),
    ENTITY_CUSTOM_FIELD("entityCustomField", null),
    ITEM_CUSTOM_FIELD("itemCustomField", null),
    ITEM_NUMBER_CUSTOM_FIELD("itemNumberCustomField", null),
    ITEM_OPTION_CUSTOM_FIELD("itemOptionCustomField", null),
    OTHER_CUSTOM_FIELD("otherCustomField", null),
    TRANSACTION_BODY_CUSTOM_FIELD("transactionBodyCustomField", null),
    TRANSACTION_COLUMN_CUSTOM_FIELD("transactionColumnCustomField", null),
    CUSTOM_RECORD_CUSTOM_FIELD("customRecordCustomField", null),
    TRANSACTION("transaction", "transaction"),
    ITEM("item", "item"),
    CUSTOM_LIST("customList", "customList"),
    CUSTOM_RECORD("customRecord", "customRecord"),
    CUSTOM_RECORD_TYPE("customRecordType", "customRecord"),
    //It's not actual CustomTransaction searhType, but for RecordTypeEnum#CustomTransaction enum type
    CUSTOM_TRANSACTION("customTransaction", "customTransaction"),
    CUSTOM_TRANSACTION_TYPE("customTransactionType", "customTransaction");

    /** Name of NetSuite record type, as defined by {@code RecordType}. */
    private String type;

    /** Name of NetSuite search record type, as defined by {@code SearchRecordType}. */
    private String searchType;

    BasicRecordType(String type, String searchType) {
        this.type = type;
        this.searchType = searchType;
    }

    public String getType() {
        return type;
    }

    public String getSearchType() {
        return searchType;
    }

    /**
     * Get basic record type enum constant by name of record type.
     *
     * @param type name of a record type
     * @return basic record type enum constant or {@code null} if type name
     *         doesn't match any known type
     */
    public static BasicRecordType getByType(String type) {
        for (BasicRecordType value : values()) {
            if (value.type.equals(type)) {
                return value;
            }
        }
        return null;
    }

}
