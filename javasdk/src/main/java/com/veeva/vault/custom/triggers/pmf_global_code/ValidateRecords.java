package com.veeva.vault.custom.triggers.pmf_global_code;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.PMF;
import com.veeva.vault.custom.udc.PMFStatus;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;

/**
 *
 * Validate inserts and updates the "PMF Global Code" object.
 *
 * Validations performed:
 *   - Bulk loads not allowed across multiple PMFs
 *   - if PMF Type != Global Renovation Project, then "Requested Implementation Date" and
 *     "Implementation Date Meaning" are required
 *   - On update, allow changes to "Global Code (SKU)" //and "Estimated Residual Cost" only at appropriate statuses
 *
 */

@RecordTriggerInfo(object = "pmf_global_code__c", events = {RecordEvent.BEFORE_INSERT, RecordEvent.BEFORE_UPDATE},
  order = TriggerOrder.NUMBER_1)
public class ValidateRecords implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      RecordEvent recordEvent = recordTriggerContext.getRecordEvent();

      String pmfId = recordTriggerContext.getRecordChanges().get(0).getNew().getValue("pmf__c", ValueType.STRING);
      String errorType = recordEvent == RecordEvent.BEFORE_UPDATE ? ErrorType.UPDATE_DENIED : ErrorType.INSERT_DENIED;

      for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
        Record newRecord = inputRecord.getNew();
        String thisPmfId = newRecord.getValue("pmf__c", ValueType.STRING);
        if (!thisPmfId.equals(pmfId)) {
          throw new RollbackException(errorType, "Bulk load of Global Codes across multiple PMF's is not allowed.");
        }
      }

      if (recordTriggerContext.getRecordChanges().size() > 1) {
        return;  // the remaining checks will be performed only on single record updates
      }

      if (recordTriggerContext.getRecordEvent() == RecordEvent.BEFORE_UPDATE) {

        String pmfStatus = PMF.getStatus(pmfId);

        Record newRecord = recordTriggerContext.getRecordChanges().get(0).getNew();
        Record oldRecord = recordTriggerContext.getRecordChanges().get(0).getOld();

        if (globalCodeChanged(oldRecord, newRecord)) {
          if (!(
            pmfStatus.equals(PMFStatus.DRAFT) ||
            pmfStatus.equals(PMFStatus.AMEND_AND_RESUBMIT) ||
            pmfStatus.equals(PMFStatus.REVIEW_COMPLETE)
          )) {
            throw new RollbackException(ErrorType.UPDATE_DENIED, "Global Code cannot be changed.");
          }
        }

//  removing this code because "Estimated Development Cost" was changed to just "Development Cost", so I suspect
//  the plant might want to update the cost after implementation...
//        if (estimatedDevelopmentCostChanged(oldRecord, newRecord)) {
//          if (!(
//            pmfStatus.equals(PMFStatus.IN_PLANT_REVIEW) ||
//            pmfStatus.equals(PMFStatus.PLANT_REVIEW_COMPLETE)
//          )) {
//            throw new RollbackException(ErrorType.UPDATE_DENIED, "Estimated Development Cost cannot be changed.");
//          }
//        }

      }

    }

    private boolean globalCodeChanged(Record oldRecord, Record newRecord) {
      String oldGlobalCode = oldRecord.getValue("global_code_sku__c", ValueType.STRING);
      String newGlobalCode = newRecord.getValue("global_code_sku__c", ValueType.STRING);
      return (
        (newGlobalCode == null && oldGlobalCode != null) ||
        (newGlobalCode != null && oldGlobalCode == null) ||
        (newGlobalCode != null && oldGlobalCode != null && !newGlobalCode.equals(oldGlobalCode))
      );
    }

//    private boolean estimatedDevelopmentCostChanged(Record oldRecord, Record newRecord) {
//      BigDecimal oldCost = oldRecord.getValue("estimated_development_cost__c", ValueType.NUMBER);
//      BigDecimal newCost = newRecord.getValue("estimated_development_cost__c", ValueType.NUMBER);
//      return (
//        (newCost == null && oldCost != null) ||
//        (newCost != null && oldCost == null) ||
//        (newCost != null && oldCost != null && !newCost.equals(oldCost))
//      );
//    }

}

