package com.veeva.vault.custom.actions.pmf;

import com.veeva.vault.custom.udc.PMF;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.action.RecordAction;
import com.veeva.vault.sdk.api.action.RecordActionContext;
import com.veeva.vault.sdk.api.action.RecordActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.LogService;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.notification.NotificationParameters;
import com.veeva.vault.sdk.api.notification.NotificationService;
import com.veeva.vault.sdk.api.notification.NotificationTemplate;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * This record action sends notification emails to local Regulatory users based on the PMF's Impacted Countries.
 *
 * The USER_ACTION usage is included for testing purposes only.
 */

@RecordActionInfo(label="Notify Countries of New PMF", object="pmf__c",
    usages={Usage.LIFECYCLE_ENTRY_ACTION, Usage.USER_ACTION})
public class NotifyCountries implements RecordAction {

    public void execute(RecordActionContext recordActionContext) {

      RecordService recordService = ServiceLocator.locate(RecordService.class);
      QueryService queryService = ServiceLocator.locate(QueryService.class);
      NotificationService notificationService = ServiceLocator.locate(NotificationService.class);
      LogService logService = ServiceLocator.locate(LogService.class);

      QueryResponse queryResponse;
      Iterator<QueryResult> queryResultIterator;

      String pmfId = recordActionContext.getRecords().get(0).getValue("id", ValueType.STRING);
      String pmfNumber = recordActionContext.getRecords().get(0).getValue("name__v", ValueType.STRING);

      queryResponse = queryService.query(
        "select id, country__c, country__cr.name__v, notification_sent__c from pmf_impacted_country__c" +
        " where pmf__c = '"+pmfId+"'"
      );

      queryResultIterator = queryResponse.streamResults().iterator();

      // this list determines who, if anyone, should receive the email based on User Role Setup...
      List<String> countriesToNotify = VaultCollections.newList();
      // this list is used to display the PMF's impacted countries in the email message...
      List<String> countryNameList = VaultCollections.newList();

      while (queryResultIterator.hasNext()) {
        QueryResult qr = queryResultIterator.next();
        boolean notificationSent = qr.getValue("notification_sent__c", ValueType.BOOLEAN);
        if (notificationSent == false) {
          countriesToNotify.add(qr.getValue("country__c", ValueType.STRING));
        }
        countryNameList.add(qr.getValue("country__cr.name__v", ValueType.STRING));
      }

      if (countriesToNotify.size() > 0) {

        String regulatoryRole = Util.getRecordID("application_role__v", "api_name__v", "regulatory__c");
        List<String> docTypeGroups = VaultCollections.newList();
        // TODO: reduce this to a single query...
        docTypeGroups.add(Util.getRecordID("doc_type_group__v", "name__v", "Artwork"));
        docTypeGroups.add(Util.getRecordID("doc_type_group__v", "name__v", "All Documents"));

        StringBuilder query = new StringBuilder();
        query
          .append("select user__v, country__c from user_role_setup__v")
          .append(" where role__v = '").append(regulatoryRole).append("'")
          .append(" and country__c contains ").append(Util.vqlContains(countriesToNotify))
          .append(" and document_type_group__c contains ").append(Util.vqlContains(docTypeGroups));

        queryResponse = queryService.query(query.toString());
        queryResultIterator = queryResponse.streamResults().iterator();

        Set<String> userIds = VaultCollections.newSet();
        List<String> countriesActuallyNotified = VaultCollections.newList();

        while (queryResultIterator.hasNext()) {
          QueryResult qr = queryResultIterator.next();
          userIds.add(qr.getValue("user__v", ValueType.STRING));
          countriesActuallyNotified.add(qr.getValue("country__c", ValueType.STRING));
        }

        NotificationParameters notificationParameters = notificationService.newNotificationParameters();
        notificationParameters
          .setRecipientsByUserIds(userIds)
          .setNotifyByEmailOnly(false);

        NotificationTemplate template = notificationService.newNotificationTemplate()
          .setTemplateName("pmf_local_regulatory_notification__c")
          .setTokenValue("pmfURL", PMF.getURL(pmfId))
          .setTokenValue("pmfNumber", pmfNumber)
          .setTokenValue("countries", String.join(", ", countryNameList));

        notificationService.send(notificationParameters, template);

        //////////////////////////////////////////////////////////////////////////////
        // Set notification_sent__c to true on all pmf_impacted_country__c records
        //////////////////////////////////////////////////////////////////////////////

          if (countriesActuallyNotified.size() > 0) {

          queryResponse = queryService.query(
            "select id from pmf_impacted_country__c" +
              " where pmf__c = '" + pmfId + "'" +
              "   and country__c contains " + Util.vqlContains(countriesActuallyNotified)
          );
          queryResultIterator = queryResponse.streamResults().iterator();

          List<Record> records = VaultCollections.newList();
          while (queryResultIterator.hasNext()) {
            String id = queryResultIterator.next().getValue("id", ValueType.STRING);
            Record record = recordService.newRecordWithId("pmf_impacted_country__c", id);
            record.setValue("notification_sent__c", true);
            records.add(record);
          }

          recordService.batchSaveRecords(records)
            .onErrors(errors -> {
              errors.stream().findFirst().ifPresent(error -> {
                String msg = error.getError().getMessage();
                // This is a benign error, so don't throw a rollback exception
                logService.error(
                  "Error trying to update field 'notification_sent__c' " +
                    "on object 'pmf_impacted_country__c'. " +
                    "Error: " + msg
                );
              });
            })
            .execute();

        } // end countriesActuallyNotified.size() > 0

      } // end for

    }

    public boolean isExecutable(RecordActionContext recordActionContext) {
      return true;
    }
}