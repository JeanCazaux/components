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
package org.talend.components.google.drive.runtime.data;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.talend.components.api.container.RuntimeContainer;
import org.talend.components.api.exception.ComponentException;
import org.talend.components.google.drive.data.GoogleDriveInputProperties;
import org.talend.components.google.drive.runtime.GoogleDriveAbstractListReader;

public class GoogleDriveInputReader extends GoogleDriveAbstractListReader {

    private int limit = 10;

    protected GoogleDriveInputReader(RuntimeContainer container, GoogleDriveDataSource source,
            GoogleDriveInputProperties properties) {
        super(container, source);
        //
        try {
            drive = source.getDriveService();
            utils = source.getDriveUtils();
        } catch (GeneralSecurityException | IOException e) {
            LOG.error(e.getMessage());
            throw new ComponentException(e);
        }
        //
        schema = properties.getDatasetProperties().getSchema();
        folderName = properties.getDatasetProperties().folder.getValue().trim();
        listModeStr = properties.getDatasetProperties().listMode.getValue().name().toUpperCase();
        includeSubDirectories = properties.getDatasetProperties().includeSubDirectories.getValue();
        includeTrashedFiles = properties.getDatasetProperties().includeTrashedFiles.getValue();
    }

    @Override
    public boolean advance() throws IOException {
        if (limit <= result.successCount) {
            return false;
        }
        return super.advance();
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

}
