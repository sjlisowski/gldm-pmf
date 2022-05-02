package com.veeva.vault.custom.actions.pmf;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.sdk.api.action.RecordAction;
import com.veeva.vault.sdk.api.action.RecordActionContext;
import com.veeva.vault.sdk.api.action.RecordActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.query.*;

/**
 * This record action verifies that all elements are addressed in order for the PMF to be complete.  Elements include:
 *
 *    - If the PMF is a Safety Variation, then all Global Codes must have a First Possible Implementation Date
 *
 * The USER_ACTION usage is included for testing purposes only.
 */

@RecordActionInfo(label="Verify PMF Completion", object="pmf__c",
    usages={Usage.LIFECYCLE_ENTRY_ACTION, Usage.USER_ACTION})
public class VerifyPmfCompletion implements RecordAction {

    public void execute(RecordActionContext recordActionContext) {

      RecordService recordService = ServiceLocator.locate(RecordService.class);

      Record pmfRecord = recordActionContext.getRecords().get(0);

      Boolean safetyVariation = pmfRecord.getValue("safety_variation__c", ValueType.BOOLEAN);

      if (safetyVariation != null && safetyVariation.booleanValue() == true) {
        verifySafetyVariationRequirements(pmfRecord);
      }

    }

    private void verifySafetyVariationRequirements(Record pmfRecord) {

        QueryService queryService = ServiceLocator.locate(QueryService.class);

        String pmfId = pmfRecord.getValue("id", ValueType.STRING);

        QueryResponse queryResponse = queryService.query(
          "select id from pmf_global_code__c where pmf__c = '"+pmfId+"' and actual_implementation_date__c = null"
        );

        if (queryResponse.getResultCount() > 0) {
          throw new RollbackException(
            ErrorType.OPERATION_DENIED,
            "\"First Possible Implementation Date\" is missing for at least one Global Code.  " +
            "For Safety Variations, \"First Possible Implementation Date\" is required for all Global Codes."
            );
        }

    }

    public boolean isExecutable(RecordActionContext recordActionContext) {
        return true;
    }
}
