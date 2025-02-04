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
package org.talend.components.azurestorage.blob.runtime;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.components.api.component.runtime.ComponentDriverInitialization;
import org.talend.components.api.container.RuntimeContainer;
import org.talend.components.api.exception.ComponentException;
import org.talend.components.api.properties.ComponentProperties;
import org.talend.components.azurestorage.blob.AzureStorageBlobService;
import org.talend.components.azurestorage.blob.AzureStorageContainerDefinition;
import org.talend.components.azurestorage.blob.tazurestoragecontainercreate.TAzureStorageContainerCreateProperties;
import org.talend.components.azurestorage.blob.tazurestoragecontainercreate.TAzureStorageContainerCreateProperties.AccessControl;
import org.talend.components.azurestorage.utils.AzureStorageUtils;
import org.talend.daikon.i18n.GlobalI18N;
import org.talend.daikon.i18n.I18nMessages;
import org.talend.daikon.properties.ValidationResult;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobContainerPublicAccessType;

/**
 * Runtime implementation for Azure storage container create feature.<br/>
 * These methods are called only on Driver node in following order: <br/>
 * 1) {@link this#initialize(RuntimeContainer, ComponentProperties)} <br/>
 * 2) {@link this#runAtDriver(RuntimeContainer)} <br/>
 * <b>Instances of this class should not be serialized and sent on worker nodes</b>
 */
public class AzureStorageContainerCreateRuntime extends AzureStorageContainerRuntime
        implements ComponentDriverInitialization<ComponentProperties> {

    private static final long serialVersionUID = -8413348199906078372L;

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureStorageContainerCreateRuntime.class);

    private static final I18nMessages messages = GlobalI18N.getI18nMessageProvider()
            .getI18nMessages(AzureStorageContainerCreateRuntime.class);

    private AccessControl access;

    /** let this attribute public for test purpose */
    public AzureStorageBlobService blobService;

    @Override
    public ValidationResult initialize(RuntimeContainer runtimeContainer, ComponentProperties properties) {
        ValidationResult validationResult = super.initialize(runtimeContainer, properties);
        if (validationResult.getStatus() == ValidationResult.Result.ERROR) {
            return validationResult;
        }

        TAzureStorageContainerCreateProperties componentProperties = (TAzureStorageContainerCreateProperties) properties;
        this.access = componentProperties.accessControl.getValue();
        this.dieOnError = componentProperties.dieOnError.getValue();
        this.blobService = new AzureStorageBlobService(getAzureConnection(runtimeContainer));

        return ValidationResult.OK;
    }

    @Override
    public void runAtDriver(RuntimeContainer runtimeContainer) {

        createAzureStorageBlobContainer();
        setReturnValues(runtimeContainer);
    }

    private void createAzureStorageBlobContainer() {

        try {

            BlobContainerPublicAccessType accessType = BlobContainerPublicAccessType.OFF;
            if (TAzureStorageContainerCreateProperties.AccessControl.Public.equals(access)) {
                accessType = BlobContainerPublicAccessType.CONTAINER;
            }

            boolean containerCreated = blobService.createContainerIfNotExist(containerName, accessType);
            if (!containerCreated) {
                LOGGER.warn(messages.getMessage("warn.ContainerExists", containerName));
            }
        } catch (StorageException | URISyntaxException | InvalidKeyException e) {
            LOGGER.error(e.getLocalizedMessage());
            if (dieOnError) {
                throw new ComponentException(e);
            }
        }
    }

    private void setReturnValues(RuntimeContainer runtimeContainer) {
        String componentId = runtimeContainer.getCurrentComponentId();
        String returnContainer = AzureStorageUtils.getStudioNameFromProperty(AzureStorageContainerDefinition.RETURN_CONTAINER);
        runtimeContainer.setComponentData(componentId, returnContainer, containerName);
    }

}
