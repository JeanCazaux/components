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
package org.talend.components.salesforce.schema;

import java.io.IOException;

/**
 * Created by pavlo.fandych on 1/30/2017.
 */
public interface SalesforceSchemaHelper<T> {

    T guessSchema(String soqlQuery) throws IOException;

    String guessQuery(T t, String entityName);
}