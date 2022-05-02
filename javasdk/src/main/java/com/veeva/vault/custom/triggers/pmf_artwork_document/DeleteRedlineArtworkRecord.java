package com.veeva.vault.custom.triggers.pmf_artwork_document;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 *
 * This trigger deletes the associated PMF Redline Artwork Document object record id.
 *
 * Note: the 'foreign key' field "pmf_artwork_document_record_id__c" is actually a text field,
 * not an object reference field.  This is because the Artwork document record could not be
 * deleted while a Redline document record references it through an object reference field.
 * It was considered to create a parent reference field and allow cascading deletes, however,
 * the true parent of PMF Redline Artwork Document records is the PMF object record.
 *
 */

@RecordTriggerInfo(object = "pmf_artwork_document__c", events = {RecordEvent.AFTER_DELETE},
  order = TriggerOrder.NUMBER_1
)
public class DeleteRedlineArtworkRecord implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);
      RecordService recordService = ServiceLocator.locate(RecordService.class);

      Set<String> ids = VaultCollections.newSet();
      recordTriggerContext.getRecordChanges().stream().forEach(recordChange -> {
        String id = recordChange.getOld().getValue("id", ValueType.STRING);
        ids.add("'" + id + "'");
      });

      String query = "select id from pmf_redline_artwork_document__c" +
              " where pmf_artwork_document_record_id__c contains (" + String.join (",", ids) + ")";

      Iterator<QueryResult> iterator = queryService.query(query).streamResults().iterator();

      List<Record> redlineArtworkRecords = VaultCollections.newList();

      while (iterator.hasNext()) {
        QueryResult qr = iterator.next();
        String pmfRedlineArtworkRecordId = qr.getValue("id", ValueType.STRING);
        redlineArtworkRecords.add(
            recordService.newRecordWithId("pmf_redline_artwork_document__c", pmfRedlineArtworkRecordId)
        );
      }

      recordService.batchDeleteRecords(redlineArtworkRecords)
          .onErrors(batchOperationErrors -> {
            batchOperationErrors.stream().findFirst().ifPresent(error -> {
              String errMsg = error.getError().getMessage();
              throw new RollbackException(
                  "OPERATION_FAILED", "Unable to delete Redline Artwork records due to: " + errMsg
              );
            });
          })
          .execute();
    }
}

