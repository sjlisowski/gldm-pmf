package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;

/**
 * Constants for the PMF Lifecycle statuses.
 */

@UserDefinedClassInfo
public class PMFStatus {
    public static final String DRAFT ="draft_state__c";
    public static final String IN_REVIEW = "in_review_state__c";
    public static final String REJECTED = "rejected_state__c";
    public static final String AMEND_AND_RESUBMIT = "amend_and_resubmit_state__c";
    public static final String CANCELED = "canceled_state__c";
    public static final String APPROVED = "approved_state__c";
    public static final String REVIEW_COMPLETE = "review_complete_state__c";
    public static final String IN_PLANT_REVIEW = "in_plant_review_state__c";
    public static final String PLANT_CHANGES_REQUESTED = "plant_changes_requested_state__c";
    public static final String PLANT_REVIEW_COMPLETE = "plant_review_complete_state__c";
    public static final String IMPLEMENTATION_IN_PROGRESS = "implementation_in_progress_state__c";
    public static final String COMPLETED = "completed_state__c";
}