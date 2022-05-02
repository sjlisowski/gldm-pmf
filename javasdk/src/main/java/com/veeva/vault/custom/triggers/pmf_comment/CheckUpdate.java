package com.veeva.vault.custom.triggers.pmf_comment;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;

/**
 * Don't allow updates by a User other than the User who created the comment.
 */

@RecordTriggerInfo(object = "pmf_comment__c", events = {RecordEvent.BEFORE_UPDATE})
public class CheckUpdate implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      if (recordTriggerContext.getRecordChanges().size() > 1) {
        throw new RollbackException(ErrorType.UPDATE_DENIED, "Bulk updates not allowed on this object");
      }

      Record newRecord = recordTriggerContext.getRecordChanges().get(0).getNew();

      String currentUserId = RequestContext.get().getCurrentUserId();
      String creatorUserId = newRecord.getValue("created_by__v", ValueType.STRING);

      if (!currentUserId.equals(creatorUserId)) {
        throw new RollbackException(ErrorType.UPDATE_DENIED, "Cannot update another User's comment.");
      }

      Record oldRecord = recordTriggerContext.getRecordChanges().get(0).getOld();
      String newPmfId = newRecord.getValue("pmf__c", ValueType.STRING);
      String oldPmfId = oldRecord.getValue("pmf__c", ValueType.STRING);

      if (!newPmfId.equals(oldPmfId)) {
        throw new RollbackException(ErrorType.UPDATE_DENIED, "Cannot change the PMF");
      }

      String userToNotify = newRecord.getValue("notified_user__c", ValueType.STRING);

      if (userToNotify != null) {
        String tag = "@" + Util.getUserFullName(userToNotify) + ": ";
        String comment = newRecord.getValue("comment__c", ValueType.STRING);
        if (!comment.contains(tag)) {
          newRecord.setValue("comment__c", tag + comment);
        }
      }

    }

}

