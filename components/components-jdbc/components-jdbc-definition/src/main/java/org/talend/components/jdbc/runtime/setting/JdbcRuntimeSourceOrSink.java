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
package org.talend.components.jdbc.runtime.setting;

import java.sql.SQLException;
import java.util.List;

import org.apache.avro.Schema;
import org.talend.components.api.component.runtime.SourceOrSink;
import org.talend.components.api.container.RuntimeContainer;
import org.talend.components.common.config.jdbc.Dbms;

/**
 * common JDBC runtime execution object
 *
 */
public interface JdbcRuntimeSourceOrSink extends SourceOrSink {

    public void setDBTypeMapping(Dbms mapping);
    
    public Schema getSchemaFromQuery(RuntimeContainer runtime, String query);
}
