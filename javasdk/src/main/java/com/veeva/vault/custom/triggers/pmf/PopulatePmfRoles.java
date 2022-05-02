package com.veeva.vault.custom.triggers.pmf;

import com.veeva.vault.custom.udc.*;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;

/**
 * This trigger populates and update roles on PMF object records with the
 * appropriate users based on records in the PMF User Role Setup object.
 *
 * Roles include:
 *
 * - Regulatory
 * - Plant Reviewer
 */

@RecordTriggerInfo(object = "pmf__c", events = {RecordEvent.AFTER_INSERT, RecordEvent.AFTER_UPDATE})
public class PopulatePmfRoles implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      PmfUsers pmfUsers = new PmfUsers();

      RecordEvent recordEvent = recordTriggerContext.getRecordEvent();

      for (RecordChange recordChange : recordTriggerContext.getRecordChanges()) {

        Record pmfNewRecord = recordChange.getNew();

        if (recordEvent == RecordEvent.AFTER_INSERT) {

          String pmfRequestor = pmfNewRecord.getValue("pmf_requestor__c", ValueType.STRING);
          String createdBy = pmfNewRecord.getValue("created_by__v", ValueType.STRING);
          if (pmfRequestor != null && !pmfRequestor.equals(createdBy)) {
            PMF.updateOwnerRole(pmfNewRecord);
          }

          PMF.updateRegulatoryRole(pmfNewRecord, pmfUsers);

        } else if (recordEvent == RecordEvent.AFTER_UPDATE) {

          Record pmfOldRecord = recordChange.getOld();

          String newCountry = pmfNewRecord.getValue("requestor_country__c", ValueType.STRING);
          String newBrand = pmfNewRecord.getValue("brand__c", ValueType.STRING);
          String newPmfType = pmfNewRecord.getValue("object_type__v", ValueType.STRING);
          String newBU = Util.getSinglePicklistValue(pmfNewRecord.getValue("business_unit__c", ValueType.PICKLIST_VALUES));
          String newLogisticSite = pmfNewRecord.getValue("logistic_site__c", ValueType.STRING);
          String newRequestor = pmfNewRecord.getValue("pmf_requestor__c", ValueType.STRING);

          String oldCountry = pmfOldRecord.getValue("requestor_country__c", ValueType.STRING);
          String oldBrand = pmfOldRecord.getValue("brand__c", ValueType.STRING);
          String oldPmfType = pmfOldRecord.getValue("object_type__v", ValueType.STRING);
          String oldBU = Util.getSinglePicklistValue(pmfOldRecord.getValue("business_unit__c", ValueType.PICKLIST_VALUES));
          String oldLogisticSite = pmfOldRecord.getValue("logistic_site__c", ValueType.STRING);
          String oldRequestor = pmfOldRecord.getValue("pmf_requestor__c", ValueType.STRING);

          if (newRequestor == null) {
            // this should never happen (see the BEFORE trigger), but just in case...
            throw new RollbackException(ErrorType.UPDATE_DENIED, "PMF Requestor is required.");
          }

          if (!newRequestor.equals(oldRequestor)) {
            PMF.updateOwnerRole(pmfNewRecord);
          }

          if (
            !newCountry.equals(oldCountry) ||
            !newBrand.equals(oldBrand) ||
            !newBU.equals(oldBU) ||
            !newPmfType.equals(oldPmfType)
          ) {
            PMF.updateRegulatoryRole(pmfNewRecord, pmfUsers);
          }

          if (
            (newLogisticSite != null && !newLogisticSite.equals(oldLogisticSite)) ||
              (oldLogisticSite != null && !oldLogisticSite.equals(newLogisticSite))
          ) {
            PMF.updatePlantReviewerRole(pmfNewRecord, pmfUsers);
          }

        }  // end if (recordEvent == RecordEvent.AFTER_UPDATE)

      }  // end for (RecordChange ...)

    } // end execute()
}

