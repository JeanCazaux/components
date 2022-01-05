//============================================================================
//
// Copyright (C) 2006-2022 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
//============================================================================
package org.talend.components.marketo.runtime.client.rest.response;

import java.util.ArrayList;
import java.util.List;

import org.talend.components.marketo.runtime.client.rest.type.SyncStatus;

public class SyncResult extends PaginateResult {

    private List<SyncStatus> result;

    public List<SyncStatus> getResult() {
        // ensure that result is never null
        if (result == null) {
            return new ArrayList<>();
        }

        return result;
    }

    public void setResult(List<SyncStatus> result) {
        this.result = result;
    }

}
