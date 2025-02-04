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

package org.talend.components.salesforce;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
import org.talend.components.api.wizard.ComponentWizard;
import org.talend.daikon.definition.DefinitionImageType;

/**
 *
 */
public class SalesforceConnectionWizardDefinitionTest {

    private final String repoLocation = "___DRI";

    private SalesforceConnectionWizardDefinition definition;
    private SalesforceConnectionProperties properties;

    @Before
    public void setUp() {
        //set default local to EN to test labels
        Locale.setDefault(Locale.ENGLISH);
        definition = new SalesforceConnectionWizardDefinition();

        properties = new SalesforceConnectionProperties("connection");
        properties.init();
    }

    @Test
    public void testSupportsProperties() {
        assertTrue(definition.supportsProperties(SalesforceConnectionProperties.class));
        assertFalse(definition.supportsProperties(SalesforceModuleListProperties.class));
    }

    @Test
    public void testCreateWizard() {
        ComponentWizard wizard = definition.createWizard(properties, repoLocation);

        assertThat(wizard, instanceOf(SalesforceConnectionWizard.class));
        assertEquals(definition, wizard.getDefinition());
        assertEquals(repoLocation, wizard.getRepositoryLocation());
    }

    @Test
    public void testImagePath() {
        assertNotNull(definition.getImagePath(DefinitionImageType.TREE_ICON_16X16));
        assertNotNull(definition.getImagePath(DefinitionImageType.WIZARD_BANNER_75X66));
        assertNull(definition.getImagePath(DefinitionImageType.SVG_ICON));
    }

    @Test
    public void testIsTopLevel() {
        assertTrue(definition.isTopLevel());
    }
    
    @Test
    public void testGetMenuItemName() {
        SalesforceConnectionWizardDefinition definition = new SalesforceConnectionWizardDefinition();
        assertEquals("Salesforce Connection", definition.getMenuItemName());
    }
}
