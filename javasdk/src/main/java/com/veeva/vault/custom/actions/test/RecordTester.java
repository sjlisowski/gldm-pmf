package com.veeva.vault.custom.actions.test;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.PMF;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.action.RecordAction;
import com.veeva.vault.sdk.api.action.RecordActionContext;
import com.veeva.vault.sdk.api.action.RecordActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.List;

/**
 * This record action sets the "Has Proposed Costs" (has_proposed_costs__c) field for a PMF.
 * This action must be invoked by a state Entry Action on state "Plant Review Complete".
 *
 * Usage USER_ACTION is for testing only.
 */

@RecordActionInfo(label="PMF Record Tester", object="pmf__c", usages={Usage.USER_ACTION})
public class RecordTester implements RecordAction {

    public void execute(RecordActionContext recordActionContext) {

      RecordService recordService = ServiceLocator.locate(RecordService.class);
      QueryService queryService = ServiceLocator.locate(QueryService.class);
      LogService logService = ServiceLocator.locate(LogService.class);

      Record record = recordActionContext.getRecords().get(0);
      String pmfRecordId = record.getValue("id", ValueType.STRING);

      testActualImplementationDateRequirement(record);
    }

    private void testActualImplementationDateRequirement(Record record) {

      String pmfId = record.getValue("id", ValueType.STRING);

      if (PMF.isSafetyVariation(pmfId)) {

        QueryService queryService = ServiceLocator.locate(QueryService.class);
        QueryResponse queryResponse = queryService.query(
          "select global_code_sku__cr.name__v from pmf_global_code__c " +
          " where pmf__c = '" + pmfId + "' and actual_implementation_date__c = null"
        );

        if (queryResponse.getResultCount() > 0) {
          List<String> skuNames = VaultCollections.newList();
          Iterator<QueryResult> iter = queryResponse.streamResults().iterator();
          while (iter.hasNext()) {
            QueryResult queryResult = iter.next();
            skuNames.add(queryResult.getValue("global_code_sku__cr.name__v", ValueType.STRING));
          }
          String errMsg = "'First Possible Implementation Date' is required for Safety Variations.  " +
            "Enter a 'First Possible Implementation Date' for the following Global Codes: " +
            Util.stringifyList(skuNames) + ".";
          throw new RollbackException(ErrorType.OPERATION_FAILED, errMsg);
        }

      }
    }

    public boolean isExecutable(RecordActionContext recordActionContext) {
        return true;
    }
}