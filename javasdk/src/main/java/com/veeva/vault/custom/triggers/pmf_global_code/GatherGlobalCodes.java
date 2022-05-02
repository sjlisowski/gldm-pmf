package com.veeva.vault.custom.triggers.pmf_global_code;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.PmfGlobalCodes;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryService;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Identify and collect all of the Global Codes (SKUs) related to a PMF, and most importantly,
 * verify that all of the Global Codes reference a single Logistic Site.
 * If more than one Logistic Site are referenced by the collection of PMF Global Codes,
 * then throw a RollbackException.
 *
 * The collection of Global Codes is used downstream to update the Logistic Site field
 * on the PMF, and to update the list of Impacted Countries.
 */

@RecordTriggerInfo(object = "pmf_global_code__c", events = {RecordEvent.BEFORE_INSERT, RecordEvent.BEFORE_UPDATE},
  order = TriggerOrder.NUMBER_2)
public class GatherGlobalCodes implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      RecordEvent recordEvent = recordTriggerContext.getRecordEvent();

      String pmfId = null;

      List<String> globalCodeRecordIds = VaultCollections.newList();
      Set<String> logisticSiteRecordIds = VaultCollections.newSet();

      List<String> updatedRecordIds = null;
      if (recordEvent == RecordEvent.BEFORE_UPDATE) {
        updatedRecordIds = VaultCollections.newList();
      }

      ////////////////////////////////////////////////////////////////////////////
      // First, get all the new or updated global code record IDs into the set...
      ////////////////////////////////////////////////////////////////////////////

      for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {

        Record newRecord = inputRecord.getNew();

        String thisPmfId = newRecord.getValue("pmf__c", ValueType.STRING);

        if (pmfId == null) {
          pmfId = thisPmfId;
        } else {
          if (!thisPmfId.equals(pmfId)) {
            throw new RollbackException(ErrorType.UPDATE_DENIED,
              "Bulk loads of Global Codes across multiple PMF's is not allowed.");
          }
        }

        String globalCodeRecordId = newRecord.getValue("global_code_sku__c", ValueType.STRING);

        if (recordEvent == RecordEvent.BEFORE_UPDATE) {
          Record oldRecord = inputRecord.getOld();
          String oldGlobalCodeRecordId = oldRecord.getValue("global_code_sku__c", ValueType.STRING);
          String newGlobalCodeRecordId = globalCodeRecordId;
          if (newGlobalCodeRecordId.equals(oldGlobalCodeRecordId)) {
            continue;  // ignore updates to fields other than the Global Code
          }
          String recordId = newRecord.getValue("id", ValueType.STRING);
          updatedRecordIds.add(recordId);
        }

        globalCodeRecordIds.add(globalCodeRecordId);
      } // end for

      if (globalCodeRecordIds.size() == 0) {
        return; // nothing to do
      }

      ///////////////////////////////////////////////////////////////////////////////////
      // Now, add the Global Codes from the existing Impacted Global Codes to the set...
      ///////////////////////////////////////////////////////////////////////////////////

      QueryService queryService = ServiceLocator.locate(QueryService.class);
      StringBuilder queryBuilder;  // we'll use these variables a couple of times
      String query;
      QueryResponse queryResponse;
      Iterator<QueryResult> qrIterator;

      queryBuilder = new StringBuilder(
        "select id, global_code_sku__c from pmf_global_code__c where pmf__c = '"
      ).append(pmfId).append("'");

      // exclude the Impacted Global Codes that are being updated
      if (recordEvent == RecordEvent.BEFORE_UPDATE) {
        Iterator<String> iter = updatedRecordIds.iterator();
        while (iter.hasNext()) {
          queryBuilder.append(" and id != '").append(iter.next()).append("'");
        }
      }

      query = queryBuilder.toString();

      queryResponse = queryService.query(query);
      qrIterator = queryResponse.streamResults().iterator();

      while (qrIterator.hasNext()) {
        String globalCodeRecordId = qrIterator.next().getValue("global_code_sku__c", ValueType.STRING);
        globalCodeRecordIds.add(globalCodeRecordId);
      }

      /////////////////////////////////////////////////////////////////////////////////////////
      // Next, query the Global Code (SKU) Object to get the Logistic Sites into a set, and
      // test whether the Set contains a single Logistic Site...
      /////////////////////////////////////////////////////////////////////////////////////////

      queryBuilder = new StringBuilder("select logistic_site__c from global_code_sku__c where id contains (");
      Iterator<String> globalCodeRecordIdsIterator = globalCodeRecordIds.iterator();
      while (globalCodeRecordIdsIterator.hasNext()) {
        queryBuilder.append("'").append(globalCodeRecordIdsIterator.next()).append("'");
        if (globalCodeRecordIdsIterator.hasNext()) {
          queryBuilder.append(", ");
        }
      }
      queryBuilder.append(")");
      query = queryBuilder.toString();

      queryResponse = queryService.query(query);
      qrIterator = queryResponse.streamResults().iterator();

      while (qrIterator.hasNext()) {
        String logisticSiteRecordId = qrIterator.next().getValue("logistic_site__c", ValueType.STRING);
        logisticSiteRecordIds.add(logisticSiteRecordId);
      }

      if (logisticSiteRecordIds.size() > 1) {
        throw new RollbackException(ErrorType.UPDATE_DENIED,
          "Impacted Global Codes reference more than one Logistic Site."
        );
      }

      /////////////////////////////////////////////////////////////////////
      // Finally, create a GlobalCodes object for downstream processing...
      /////////////////////////////////////////////////////////////////////
      String logisticSiteRecordId = logisticSiteRecordIds.iterator().next();

      PmfGlobalCodes globalCodes = new PmfGlobalCodes(pmfId, globalCodeRecordIds, logisticSiteRecordId);

      RequestContext.get().setValue(PmfGlobalCodes.ContextName, globalCodes);

    } // end execute()
}

