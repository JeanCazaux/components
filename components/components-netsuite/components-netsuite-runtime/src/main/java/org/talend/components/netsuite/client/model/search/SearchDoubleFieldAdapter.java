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

package org.talend.components.netsuite.client.model.search;

import static org.talend.components.netsuite.client.model.beans.Beans.setProperty;
import static org.talend.components.netsuite.client.model.beans.Beans.setSimpleProperty;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.talend.components.netsuite.client.model.BasicMetaData;

/**
 * Search field adapter for {@code SearchDoubleField} and {@code SearchDoubleCustomField}.
 */
public class SearchDoubleFieldAdapter<T> extends SearchFieldAdapter<T> {

    public SearchDoubleFieldAdapter(BasicMetaData metaData, SearchFieldType fieldType, Class<T> fieldClass) {
        super(metaData, fieldType, fieldClass);
    }

    @Override
    public T populate(T fieldObject, String internalId, String operatorName, List<String> values) {
        T nsObject = fieldObject != null ? fieldObject : createField(internalId);

        if (values != null && values.size() != 0) {
            setProperty(nsObject, "searchValue", Double.valueOf(Double.parseDouble(values.get(0))));
            if (values.size() > 1 && StringUtils.isNotEmpty(values.get(1))) {
                setSimpleProperty(nsObject, "searchValue2", Double.valueOf(Double.parseDouble(values.get(1))));
            }
        }

        setSimpleProperty(nsObject, "operator",
                metaData.getSearchFieldOperatorByName(fieldType.getFieldTypeName(), operatorName));

        return nsObject;
    }
}
