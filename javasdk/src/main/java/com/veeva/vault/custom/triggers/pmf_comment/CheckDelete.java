package com.veeva.vault.custom.triggers.pmf_comment;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;

/**
 * Don't allow a User to delete another User's comment.
 */

@RecordTriggerInfo(object = "pmf_comment__c", events = {RecordEvent.BEFORE_DELETE})
public class CheckDelete implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      if (recordTriggerContext.getRecordChanges().size() > 1) {
        throw new RollbackException(ErrorType.UPDATE_DENIED, "Bulk deletes not allowed on this object");
      }

      Record record = recordTriggerContext.getRecordChanges().get(0).getOld();

      String currentUserId = RequestContext.get().getCurrentUserId();
      String creatorUserId = record.getValue("created_by__v", ValueType.STRING);

      if (!currentUserId.equals(creatorUserId)) {
        throw new RollbackException(ErrorType.DELETION_DENIED, "Cannot delete another User's comment.");
      }

    }

}

