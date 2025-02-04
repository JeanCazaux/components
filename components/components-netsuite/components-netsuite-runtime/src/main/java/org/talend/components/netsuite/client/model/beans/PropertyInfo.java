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

package org.talend.components.netsuite.client.model.beans;

import java.lang.reflect.Method;

/**
 * Descriptor of bean's property.
 *
 * <p>This is simplified version of {@link java.beans.PropertyDescriptor} and is intended
 * to be used for beans generated from NetSuite's XML schemas.
 */
public class PropertyInfo {
    private String name;
    private Class<?> readType;
    private Class<?> writeType;
    private String readMethodName;
    private String writeMethodName;

    public PropertyInfo(String name, Class<?> readType, Class<?> writeType,
            Method readMethod, Method writeMethod) {
        this(name, readType, writeType,
                readMethod != null ? readMethod.getName() : null,
                writeMethod != null ? writeMethod.getName() : null);
    }

    public PropertyInfo(String name, Class<?> readType, Class<?> writeType,
            String readMethodName, String writeMethodName) {
        this.name = name;
        this.readType = readType;
        this.writeType = writeType;
        this.readMethodName = readMethodName;
        this.writeMethodName = writeMethodName;
    }

    public String getName() {
        return name;
    }

    public Class<?> getReadType() {
        return readType;
    }

    public Class<?> getWriteType() {
        return writeType;
    }

    public String getReadMethodName() {
        return readMethodName;
    }

    public String getWriteMethodName() {
        return writeMethodName;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PropertyInfo{");
        sb.append("name='").append(name).append('\'');
        sb.append(", readType=").append(readType);
        sb.append(", writeType=").append(writeType);
        sb.append(", readMethodName='").append(readMethodName).append('\'');
        sb.append(", writeMethodName='").append(writeMethodName).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
