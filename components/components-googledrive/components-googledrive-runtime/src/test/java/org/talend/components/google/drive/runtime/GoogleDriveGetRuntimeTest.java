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
package org.talend.components.google.drive.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.talend.components.google.drive.runtime.GoogleDriveRuntime.getStudioName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.talend.components.api.exception.ComponentException;
import org.talend.components.google.drive.GoogleDriveMimeTypes;
import org.talend.components.google.drive.get.GoogleDriveGetDefinition;
import org.talend.components.google.drive.get.GoogleDriveGetProperties;
import org.talend.daikon.properties.ValidationResult;
import org.talend.daikon.properties.ValidationResult.Result;

import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

public class GoogleDriveGetRuntimeTest extends GoogleDriveTestBaseRuntime {

    public static final String FILE_GET_ID = "fileName-get-id";

    private GoogleDriveGetRuntime testRuntime;

    GoogleDriveGetProperties properties;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        properties = new GoogleDriveGetProperties("test");
        properties.setupProperties();
        properties = (GoogleDriveGetProperties) setupConnectionWithInstalledApplicationWithJson(properties);
        //
        properties.file.setValue("google-drive-get");

        testRuntime = spy(GoogleDriveGetRuntime.class);
        doReturn(drive).when(testRuntime).getDriveService();

        FileList fileList = new FileList();
        List<File> files = new ArrayList<>();
        File f = new File();
        f.setId(FILE_GET_ID);
        files.add(f);
        fileList.setFiles(files);

        when(drive
                .files()
                .list()
                .setQ(anyString())
                .setSupportsAllDrives(false)
                .setIncludeItemsFromAllDrives(false).execute()).thenReturn(fileList);

        File file = new File();
        file.setId("fileName-id");
        file.setMimeType(GoogleDriveMimeTypes.MIME_TYPE_JSON);
        file.setFileExtension("json");
        when(drive
                .files()
                .get(any())
                .setFields(anyString())
                .setSupportsAllDrives(false).execute()).thenReturn(file);
    }

    @Test
    public void testRunAtDriver() throws Exception {
        ValidationResult vr = testRuntime.initialize(container, properties);
        assertNotNull(vr);
        assertEquals(Result.OK, vr.getStatus());
        testRuntime.runAtDriver(container);
        assertEquals(FILE_GET_ID,
                container.getComponentData(TEST_CONTAINER, getStudioName(GoogleDriveGetDefinition.RETURN_FILE_ID)));
        assertNull(container.getComponentData(TEST_CONTAINER, getStudioName(GoogleDriveGetDefinition.RETURN_CONTENT)));
    }

    @Test
    public void testRunAtDriverWithPath() throws Exception {
        String qA = "name='A' and 'root' in parents and mimeType='application/vnd.google-apps.folder'";
        String qB = "name='B' and 'A' in parents and mimeType='application/vnd.google-apps.folder'";
        String qC = "name='C' and 'B' in parents and mimeType='application/vnd.google-apps.folder'";
        File file = new File();
        file.setId("fileName-id");
        file.setMimeType(GoogleDriveMimeTypes.MIME_TYPE_GOOGLE_DOCUMENT);

        when(drive
                .files()
                .list()
                .setQ(qA)
                .setSupportsAllDrives(false)
                .setIncludeItemsFromAllDrives(false)
                .execute()).thenReturn(createFolderFileList("A", false));
        when(drive
                .files()
                .list()
                .setQ(qB)
                .setSupportsAllDrives(false)
                .setIncludeItemsFromAllDrives(false)
                .execute()).thenReturn(createFolderFileList("B", false));
        when(drive
                .files()
                .list()
                .setQ(qC)
                .setSupportsAllDrives(false)
                .setIncludeItemsFromAllDrives(false)
                .execute()).thenReturn(createFolderFileList("C", false));
        when(drive
                .files()
                .get(anyString())
                .setFields(anyString())
                .setSupportsAllDrives(false).execute()).thenReturn(file);
        //
        properties.file.setValue("/A/B/C");
        testRuntime.initialize(container, properties);
        testRuntime.runAtDriver(container);
        assertEquals("C", container.getComponentData(TEST_CONTAINER, getStudioName(GoogleDriveGetDefinition.RETURN_FILE_ID)));
        assertNull(container.getComponentData(TEST_CONTAINER, getStudioName(GoogleDriveGetDefinition.RETURN_CONTENT)));
    }

    @Test
    public void testRunAtDriverForGoogleDoc() throws Exception {
        File file = new File();
        file.setId("fileName-id");
        file.setMimeType(GoogleDriveMimeTypes.MIME_TYPE_GOOGLE_DOCUMENT);
        when(drive
                .files()
                .get(any())
                .setFields(anyString())
                .setSupportsAllDrives(false).execute()).thenReturn(file);
        //
        testRuntime.initialize(container, properties);
        testRuntime.runAtDriver(container);
        assertEquals(FILE_GET_ID,
                container.getComponentData(TEST_CONTAINER, getStudioName(GoogleDriveGetDefinition.RETURN_FILE_ID)));
        assertNull(container.getComponentData(TEST_CONTAINER, getStudioName(GoogleDriveGetDefinition.RETURN_CONTENT)));
    }

    @Test
    public void testRunAtDriverForDownloadFile() throws Exception {
        properties.storeToLocal.setValue(true);
        properties.outputFileName.setValue(getClass().getClassLoader().getResource(".").toURI().getPath() + FILE_GET_ID);
        properties.setOutputExt.setValue(true);
        //
        testRuntime.initialize(container, properties);
        testRuntime.runAtDriver(container);
        assertEquals(FILE_GET_ID,
                container.getComponentData(TEST_CONTAINER, getStudioName(GoogleDriveGetDefinition.RETURN_FILE_ID)));
        assertNull(container.getComponentData(TEST_CONTAINER, getStudioName(GoogleDriveGetDefinition.RETURN_CONTENT)));
    }

    @Test
    public void testFailedValidation() throws Exception {
        properties.file.setValue("");
        ValidationResult vr = testRuntime.initialize(container, properties);
        assertNotNull(vr);
        assertEquals(Result.ERROR, vr.getStatus());
    }

    @Test
    public void testExceptionThrown() throws Exception {
        when(drive
                .files()
                .list()
                .setQ(anyString())
                .setSupportsAllDrives(false)
                .setIncludeItemsFromAllDrives(false)
                .execute()).thenThrow(new IOException("error"));
        testRuntime.initialize(container, properties);
        try {
            testRuntime.runAtDriver(container);
            fail("Should not be here");
        } catch (Exception e) {
        }
    }

    @Test(expected = ComponentException.class)
    public void testNonExistentFile() throws Exception {
        String q1 = "name='A' and 'root' in parents and mimeType='application/vnd.google-apps.folder'";
        when(drive
                .files()
                .list()
                .setQ(q1)
                .setSupportsAllDrives(false)
                .setIncludeItemsFromAllDrives(false)
                .execute()).thenReturn(emptyFileList);
        when(drive
                .files()
                .list()
                .setQ(anyString())
                .setSupportsAllDrives(false)
                .setIncludeItemsFromAllDrives(false).execute()).thenReturn(emptyFileList);
        //
        properties.file.setValue("/A");
        testRuntime.initialize(container, properties);
        testRuntime.runAtDriver(container);
        fail("Should not be here");
    }

    @Test(expected = ComponentException.class)
    public void testManyFiles() throws Exception {
        FileList files = new FileList();
        List<File> fl = new ArrayList<>();
        File f1 = new File();
        fl.add(f1);
        File f2 = new File();
        fl.add(f2);
        files.setFiles(fl);
        String q1 = "name='A' and 'root' in parents and mimeType='application/vnd.google-apps.folder'";
        when(drive
                .files()
                .list()
                .setQ(q1)
                .setSupportsAllDrives(false)
                .setIncludeItemsFromAllDrives(false)
                .execute()).thenReturn(files);
        when(drive
                .files()
                .list()
                .setQ(any())
                .setSupportsAllDrives(false)
                .setIncludeItemsFromAllDrives(false).execute()).thenReturn(files);
        //
        properties.file.setValue("/A");
        testRuntime.initialize(container, properties);
        testRuntime.runAtDriver(container);
        fail("Should not be here");
    }

}
