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
package org.talend.components.jdbc.wizard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Ignore;
import org.junit.Test;
import org.talend.components.api.exception.ComponentException;
import org.talend.daikon.properties.presentation.Form;

/**
 * The class <code>JDBCConnectionWizardPropertiesTest</code> contains tests for the class
 * <code>{@link JDBCConnectionWizardProperties}</code>.
 *
 * @generatedBy CodePro at 17-6-23 PM3:31
 * @author wangwei
 * @version $Revision: 1.0 $
 */
public class JDBCConnectionWizardPropertiesTest {

    /**
     * Run the JDBCConnectionWizardProperties(String) constructor test.
     *
     * @throws Exception
     *
     * @generatedBy CodePro at 17-6-23 PM3:31
     */
    @Test
    public void testJDBCConnectionWizardProperties() throws Exception {
        String name = "wizard";

        JDBCConnectionWizardProperties result = new JDBCConnectionWizardProperties(name);

        assertNotNull(result.getRuntimeSetting());
        assertEquals("properties.wizard.displayName", result.getDisplayName());
        assertEquals(name, result.getName());
        assertEquals(name, result.getTitle());
    }

    /**
     * Run the void refreshLayout(Form) method test.
     *
     * @throws Exception
     *
     * @generatedBy CodePro at 17-6-23 PM3:31
     */
    @Test
    public void testRefreshLayout() throws Exception {
        JDBCConnectionWizardProperties fixture = new JDBCConnectionWizardProperties("wizard");
        Form form = fixture.getForm(Form.MAIN);
        fixture.refreshLayout(form);

    }

    /**
     * Run the void setupLayout() method test.
     *
     * @throws Exception
     *
     * @generatedBy CodePro at 17-6-23 PM3:31
     */
    @Test
    public void testSetupLayout() throws Exception {
        JDBCConnectionWizardProperties fixture = new JDBCConnectionWizardProperties("");
        fixture.init();
        Form main = fixture.getForm(Form.MAIN);
        assertNotNull(main);
    }

    /**
     * Run the void setupProperties() method test.
     *
     * @throws Exception
     *
     * @generatedBy CodePro at 17-6-23 PM3:31
     */
    @Test
    public void testSetupProperties() throws Exception {
        JDBCConnectionWizardProperties fixture = new JDBCConnectionWizardProperties("");
        fixture.init();
        fixture.setupProperties();
    }

    /**
     * Run the ValidationResult validateTestConnection() method test.
     *
     * @throws Exception
     *
     * @generatedBy CodePro at 17-6-23 PM3:31
     */
    @Ignore
    @Test(expected = ComponentException.class)
    public void testValidateTestConnection() throws Exception {
        JDBCConnectionWizardProperties fixture = new JDBCConnectionWizardProperties("");
        fixture.init();
        fixture.validateTestConnection();
    }

}