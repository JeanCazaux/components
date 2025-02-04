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
package org.talend.components.salesforce.runtime.dataprep;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.talend.components.salesforce.datastore.SalesforceDatastoreProperties;
import org.talend.components.salesforce.runtime.DisableIfMissingConfig;
import org.talend.daikon.properties.ValidationResult;

public class SalesforceDatastoreRuntimeTestIT {
    @ClassRule
    public static final TestRule DISABLE_IF_NEEDED = new DisableIfMissingConfig();

    @Test
    public void testDoHealthChecksWithSuccess() {
        SalesforceDatastoreProperties datastore = new SalesforceDatastoreProperties("datastore");
        CommonTestUtils.setValueForDatastoreProperties(datastore);

        SalesforceDatastoreRuntime runtime = new SalesforceDatastoreRuntime();
        runtime.initialize(null, datastore);
        Iterable<ValidationResult> results = runtime.doHealthChecks(null);

        Assert.assertNotNull(results);
        for (ValidationResult result : results) {
            Assert.assertTrue(result.getMessage(), result.getStatus() == ValidationResult.Result.OK);
        }
    }

    /*
    * If the logic changes for this test please specify appropriate timeout.
    * The average execution time for this test less than 1 sec.
    */
    @Test(timeout = 30_000)
    public void testDoHealthChecksWithFail() {
        SalesforceDatastoreProperties datastore = new SalesforceDatastoreProperties("datastore");
        CommonTestUtils.setValueForDatastoreProperties(datastore);
        datastore.password.setValue("wrongone");

        SalesforceDatastoreRuntime runtime = new SalesforceDatastoreRuntime();
        runtime.initialize(null, datastore);
        Iterable<ValidationResult> results = runtime.doHealthChecks(null);

        Assert.assertNotNull(results);
        for (ValidationResult result : results) {
            Assert.assertTrue(result.getMessage(), result.getStatus() == ValidationResult.Result.ERROR);
            Assert.assertNotNull(result.getMessage());
        }
    }

}
