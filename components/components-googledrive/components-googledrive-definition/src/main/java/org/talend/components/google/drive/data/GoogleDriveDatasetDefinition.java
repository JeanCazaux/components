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
package org.talend.components.google.drive.data;

import org.talend.components.common.dataset.DatasetDefinition;
import org.talend.components.google.drive.GoogleDriveComponentDefinition;
import org.talend.daikon.definition.DefinitionImageType;
import org.talend.daikon.definition.I18nDefinition;
import org.talend.daikon.runtime.RuntimeInfo;

public class GoogleDriveDatasetDefinition extends I18nDefinition implements DatasetDefinition<GoogleDriveDatasetProperties> {

    public static final String NAME = "GoogleDriveDataset";

    public GoogleDriveDatasetDefinition() {
        super(NAME);
    }

    @Override
    public RuntimeInfo getRuntimeInfo(GoogleDriveDatasetProperties properties) {
        return GoogleDriveComponentDefinition.getCommonRuntimeInfo(GoogleDriveComponentDefinition.DATASET_RUNTIME_CLASS);
    }

    @Override
    public Class<GoogleDriveDatasetProperties> getPropertiesClass() {
        return GoogleDriveDatasetProperties.class;
    }

    @Override
    public String getImagePath() {
        return NAME + "_icon32.png";
    }

    @Override
    public String getImagePath(DefinitionImageType definitionImageType) {
        switch (definitionImageType) {
        case PALETTE_ICON_32X32:
            return NAME + "_icon32.png";
        default:
            return null;
        }
    }

    @Override
    public String getIconKey() {
        return null;
    }
}
