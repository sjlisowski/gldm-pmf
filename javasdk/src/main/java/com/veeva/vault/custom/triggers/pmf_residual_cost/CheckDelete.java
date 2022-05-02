package com.veeva.vault.custom.triggers.pmf_residual_cost;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.PMF;
import com.veeva.vault.custom.udc.PMFStatus;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryService;

/**
 * This trigger disallows deletion of records in the PMF Residual Cost (pmf_residual_cost__c) in certain
 * PMF Lifecycle states:
 *
 *   - Implementation In Progress
 *   - Completed
 *   - Canceled
 *   - Rejected
 */

@RecordTriggerInfo(object = "pmf_residual_cost__c", events = {RecordEvent.BEFORE_DELETE})
public class CheckDelete implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      if (recordTriggerContext.getRecordChanges().size() > 1) {
        return;  // this trigger will not validate bulk insert/updates
      }

      Record record = recordTriggerContext.getRecordChanges().get(0).getOld();

      String pmfId = getPmfId(record);
      String pmfStatus = PMF.getStatus(pmfId);

      if (pmfStatus.equals(PMFStatus.IMPLEMENTATION_IN_PROGRESS)
       || pmfStatus.equals(PMFStatus.COMPLETED)
       || pmfStatus.equals(PMFStatus.CANCELED)
       || pmfStatus.equals(PMFStatus.REJECTED)
      ) {
        throw new RollbackException(ErrorType.OPERATION_DENIED, "Deletion of PMF Residual Cost is not allowed.");
      }
    }

    private String getPmfId(Record record) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);
      String pmfGlobalCodeId = record.getValue("pmf_global_code_sku__c", ValueType.STRING);

      QueryResponse queryResponse = queryService.query(
        "select pmf__c from pmf_global_code__c where id = '"+pmfGlobalCodeId+"'"
      );

      String pmfId = queryResponse
        .streamResults()
        .iterator()
        .next()
        .getValue("pmf__c", ValueType.STRING);

      return pmfId;
    }

}
