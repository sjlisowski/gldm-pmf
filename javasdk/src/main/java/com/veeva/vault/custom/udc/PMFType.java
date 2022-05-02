package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;

/**
 * Types of PMF Object Type by api name.
 */

@UserDefinedClassInfo
public class PMFType {
    public static final String ArtworkModification = "artwork_modification__c";
    public static final String NonArtworkModification = "non_artwork_modification__c";
    public static final String GlobalRenovationProject = "global_renovation_project__c";
}