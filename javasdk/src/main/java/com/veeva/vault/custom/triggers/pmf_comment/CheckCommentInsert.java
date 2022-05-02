package com.veeva.vault.custom.triggers.pmf_comment;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;

/**
 *
 * This trigger:
 *   - denies bulk inserts
 *   - prepends the notified persons name to the message like "@John Doe: ..."
 *
 */

@RecordTriggerInfo(object = "pmf_comment__c", events = {RecordEvent.BEFORE_INSERT})
public class CheckCommentInsert implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      if (recordTriggerContext.getRecordChanges().size() > 1) {
        throw new RollbackException(ErrorType.INSERT_DENIED, "Bulk inserts not allowed on this object");
      }

      Record newRecord = recordTriggerContext.getRecordChanges().get(0).getNew();

      String userToNotify = newRecord.getValue("notified_user__c", ValueType.STRING);

      if (userToNotify != null) {
        String comment =
          "@" + Util.getUserFullName(userToNotify) + ": " + newRecord.getValue("comment__c", ValueType.STRING);
        newRecord.setValue("comment__c", comment);
      }

    }
}

