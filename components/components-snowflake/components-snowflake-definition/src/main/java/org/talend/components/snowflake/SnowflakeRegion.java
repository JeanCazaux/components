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
package org.talend.components.snowflake;

/**
 * Snowflake region specifies where data is geographically stored and compute
 * resources are provisioned. Each Snowflake account is located in a single
 * region.
 */
public enum SnowflakeRegion {

    AWS_US_WEST(""), // no region ID for default US West
    AWS_US_EAST_1("us-east-1"),
    AWS_EU_WEST_1("eu-west-1"),
    AWS_EU_CENTRAL_1("eu-central-1"),
    AWS_AP_SOUTHEAST_2("ap-southeast-2"),
    AZURE_EAST_US_2("east-us-2.azure");

    /**
     * Each Snowflake Region except of US West has a region ID. For regions except
     * of US WEST the ID is a required segment in the URL for accessing Snowflake
     * account.
     */
    private final String regionID;

    SnowflakeRegion(String region){
        this.regionID = region;
    }

    public String getRegionID() {
        return regionID;
    }
}
