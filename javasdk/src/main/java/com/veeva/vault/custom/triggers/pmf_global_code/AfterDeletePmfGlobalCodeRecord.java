package com.veeva.vault.custom.triggers.pmf_global_code;

import com.veeva.vault.custom.udc.PmfGlobalCodes;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 *
 * This trigger updates the PMF's list of Impacted Countries.
 *
 * The deletion of PMF Global Code records across multiple PMF's is expected to be a rare
 * occurrence if it happens at all.  Therefore, this trigger uses the simple approach of
 * embedding the query inside the 'for' loop.
 *
 */

@RecordTriggerInfo(object = "pmf_global_code__c", events = {RecordEvent.AFTER_DELETE})
public class AfterDeletePmfGlobalCodeRecord implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      Set<String> pmfIds = VaultCollections.newSet();

      for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
        pmfIds.add(inputRecord.getOld().getValue("pmf__c", ValueType.STRING));
      }

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      for (String pmfId : pmfIds) {

        List<String> globalCodeIds = VaultCollections.newList();

        QueryResponse qResponse = queryService.query(
          "select global_code_sku__c from pmf_global_code__c where pmf__c = '"+pmfId+"'"
        );
        Iterator<QueryResult> iter = qResponse.streamResults().iterator();

        while (iter.hasNext()) {
          globalCodeIds.add(iter.next().getValue("global_code_sku__c", ValueType.STRING));
        }

        PmfGlobalCodes pmfGlobalCodes = new PmfGlobalCodes(pmfId, globalCodeIds);

        pmfGlobalCodes.updateImpactedCountries();
        pmfGlobalCodes.updatePmfGlobalCodesAndCountriesDisplay();

      } // end for
    	
    } // end execute
}

