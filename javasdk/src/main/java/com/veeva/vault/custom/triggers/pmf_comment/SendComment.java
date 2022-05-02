package com.veeva.vault.custom.triggers.pmf_comment;

import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.StringUtils;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Set;

/**
 * This trigger notifies the user referenced on a new PMF comment.
 */

@RecordTriggerInfo(object = "pmf_comment__c", events = {RecordEvent.AFTER_INSERT, RecordEvent.AFTER_UPDATE})
public class SendComment implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {

        Record newRecord = inputRecord.getNew();

        String userToNotify = newRecord.getValue("notified_user__c", ValueType.STRING);

        if (userToNotify != null) {

          Set recipients = VaultCollections.newSet();
          recipients.add(userToNotify);

          String pmfTitle = getPmfTitle(newRecord);

          String subject = "Notification: Mention in comment on PMF \""+pmfTitle+"\"";
          String message = getMessageBody(newRecord, pmfTitle);

          Util.sendNotificationSimple(recipients, subject, message);

        }

      }
    	
    }

    private String getMessageBody(Record record, String pmfTitle) {

      StringBuilder message = new StringBuilder();
      String creatorFullName = Util.getUserFullName(record.getValue("created_by__v", ValueType.STRING));
      String pmfId = record.getValue("pmf__c", ValueType.STRING);
      String commentURL = Util.getObjectRecordURL("pmf__c", pmfId) + "?expanded=pmf_comments__c&s=0";
      String commentText = record.getValue("comment__c", ValueType.STRING);
      commentText = StringUtils.replaceAll(commentText, "\n", "<br />");

      message
        .append("<p><b>").append(creatorFullName).append("</b> mentioned you in a comment on PMF ")
        .append("<a href=").append(commentURL).append(">").append(pmfTitle).append("</a>:</p>")
        .append("<p><ul><li><pre>").append(commentText).append("</pre></li></ul></p>")
        .append("<p>The above link will take you directly to the comment where you are mentioned.</p>");

      return message.toString();
    }

    private String getPmfTitle(Record record) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);
      String pmfId = record.getValue("pmf__c", ValueType.STRING);
      String title;

      QueryResponse queryResponse = queryService.query(
        "select title__c from pmf__c where id = '"+pmfId+"'"
      );

      if (queryResponse.getResultCount() > 0) {
        title = queryResponse.streamResults().findFirst().get().getValue("title__c", ValueType.STRING);
      } else {
        title = "No title";
      }

      return title;
    }

}

