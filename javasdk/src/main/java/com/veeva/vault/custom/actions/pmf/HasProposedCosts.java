package com.veeva.vault.custom.actions.pmf;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.sdk.api.action.RecordAction;
import com.veeva.vault.sdk.api.action.RecordActionContext;
import com.veeva.vault.sdk.api.action.RecordActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryService;

/**
 * This record action sets the "Has Proposed Costs" (has_proposed_costs__c) field for a PMF.
 * This action must be invoked by a state Entry Action on state "Plant Review Complete".
 *
 * Usage USER_ACTION is for testing only.
 */

@RecordActionInfo(label="Has Proposed Costs", object="pmf__c",
  usages={Usage.LIFECYCLE_ENTRY_ACTION, Usage.USER_ACTION})
public class HasProposedCosts implements RecordAction {

    private static final String STATUS_PROPOSED = "proposed__c";
    private static final String HAS_PROPOSED_COSTS = "has_proposed_costs__c";

    public void execute(RecordActionContext recordActionContext) {

      RecordService recordService = ServiceLocator.locate(RecordService.class);
      QueryService queryService = ServiceLocator.locate(QueryService.class);
      LogService logService = ServiceLocator.locate(LogService.class);

      Record record = recordActionContext.getRecords().get(0);
      String pmfRecordId = record.getValue("id", ValueType.STRING);

      String query =
        "select id" +
          "  from pmf_residual_cost__c " +
          " where global_code_sku__cr.pmf__c = '"+pmfRecordId+"'" +
          "   and cost_status__c = '"+STATUS_PROPOSED+"'";

      logService.debug(query);

      QueryResponse queryResponse = queryService.query(query);

      boolean hasProposedCosts = queryResponse.streamResults().count() > 0;

      logService.debug("Has proposed costs: {}", hasProposedCosts);

      record.setValue(HAS_PROPOSED_COSTS, hasProposedCosts);

      recordService.batchSaveRecords(VaultCollections.asList(record))
        .onErrors(batchOperationErrors -> {
          batchOperationErrors.stream().findFirst().ifPresent(error -> {
            String errMsg = error.getError().getMessage();
            throw new RollbackException(ErrorType.OPERATION_FAILED, errMsg);
          });
        })
        .execute();

    }

    public boolean isExecutable(RecordActionContext recordActionContext) {
        return true;
    }
}