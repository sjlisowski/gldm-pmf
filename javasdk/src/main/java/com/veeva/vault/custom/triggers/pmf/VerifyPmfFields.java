package com.veeva.vault.custom.triggers.pmf;

import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;

/**
 *
 * This trigger:
 *
 *   Sets the PMF Requestor field to the same user ID as the Created By field
 *   if the PMF Requestor field is null on insert or update.
 *
 */

@RecordTriggerInfo(object = "pmf__c", events = {RecordEvent.BEFORE_INSERT, RecordEvent.BEFORE_UPDATE})
public class VerifyPmfFields implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {

        Record newRecord = inputRecord.getNew();

        // Verify PMF Requestor field...
        String pmfRequestor = newRecord.getValue("pmf_requestor__c", ValueType.STRING);
        if (pmfRequestor == null) {
          String createdBy = newRecord.getValue("created_by__v", ValueType.STRING);
          newRecord.setValue("pmf_requestor__c", createdBy);
        }

      }
    	
    }
}

