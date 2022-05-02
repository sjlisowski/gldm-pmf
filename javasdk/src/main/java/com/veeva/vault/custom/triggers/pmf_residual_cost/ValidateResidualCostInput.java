package com.veeva.vault.custom.triggers.pmf_residual_cost;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.PMF;
import com.veeva.vault.custom.udc.PMFStatus;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryService;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * This trigger verifies inserts and updates to records in the PMF Residual Cost (pmf_residual_cost__c).
 *
 * Access to the "PMF Residual Cost" (pmf_residual_cost__c) object is shared between the PMF Requestor
 * and the Plant Reviewer.
 *
 * Specific permissions are controlled in two Permission Sets as follows:
 *
 *    PMF Requestor
 *       - Can edit field "Cost Status"
 *
 *    PMF Plant Reviewer
 *       - Can create new records
 *       - Can delete records
 *       - Can edit all fields:
 *         - Cost Status
 *         - Proposed Implementation Date
 *         - Estimated Residual Cost
 *        // - Actual Residual Cost (this field was moved to the "PMF Global Code" object
 *
 *   These Permission Sets must be maintained strictly in order to insure the integrity
 *   of this part of the PMF process.
 *
 *   This trigger is concerned the following:
 *
 *     - State in which inserts and updates are allowed.
 *
 *     - With regard to the "Cost Status" field:
 *        - Plant Reviewers must not be allowed select the "Accepted" status.
 *        - PMF Requestors must not be allowed to select the "Final" status.
 *        - If the Plant Reviewer creates a Residual Cost in the "Final" status,
 *          the Plant Reviewer must not be allowed to add more than one Residual
 *          Cost to the Global Code.
 */

@RecordTriggerInfo(object = "pmf_residual_cost__c", events = {RecordEvent.BEFORE_INSERT, RecordEvent.BEFORE_UPDATE})
public class ValidateResidualCostInput implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      if (recordTriggerContext.getRecordChanges().size() > 1) {
        return;  // this trigger will not validate bulk insert/updates
      }

      RecordEvent recordEvent = recordTriggerContext.getRecordEvent();

      Record record = recordTriggerContext.getRecordChanges().get(0).getNew();

      String pmfId = getPmfId(record);
      String pmfStatus = PMF.getStatus(pmfId);
      String pmfRequestorId = PMF.getRequestor(pmfId);
      String currentUserId = RequestContext.get().getCurrentUserId();
      String costStatus = Util.getSinglePicklistValue(
        record.getValue("cost_status__c", ValueType.PICKLIST_VALUES)
      );

      // Cost Status is a required field, but Users can still try to save a record without a
      // cost status.  This check eliminates null pointer exceptions.
      if (costStatus == null) {
        throw new RollbackException(ErrorType.OPERATION_FAILED, "Cost Status is required");
      }

      if (recordEvent == RecordEvent.BEFORE_INSERT) {
        checkInsertsAllowed(pmfStatus);
      }

      if (recordEvent == RecordEvent.BEFORE_UPDATE) {
        checkUpdatesAllowed(recordTriggerContext, pmfStatus);
      }

      /////////////////////////////////////////////////
      // Restrictions for the Requestor...
      /////////////////////////////////////////////////

      if (currentUserId.equals(pmfRequestorId)) {

        if (costStatus.equals("final__c")) {
          throw new RollbackException(ErrorType.UPDATE_DENIED, "Cannot set Cost Status to \"Final\"");
        }

        if (costStatus.equals("accepted__c")) {
          if (globalCodeHasExistingResidualCostInStatus(record, "accepted__c")) {
            throw new RollbackException(ErrorType.UPDATE_DENIED, "Cannot accept more than one Residual Cost.");
          }
        }
      }

      /////////////////////////////////////////////////
      // Restrictions for the Plant...
      /////////////////////////////////////////////////

      if (!currentUserId.equals(pmfRequestorId)) {  // assumed to be the Plant Reviewer

        if (recordEvent == RecordEvent.BEFORE_INSERT) {
          if (costStatus.equals("accepted__c")) {
            throw new RollbackException(ErrorType.INSERT_DENIED,
               "Setting the \"Accepted\" status for a new Residual Cost is not allowed."
            );
          }
        }

        if (recordEvent == RecordEvent.BEFORE_UPDATE) {
          if (costStatusChanged(recordTriggerContext)) {
            if (costStatus.equals("accepted__c")) {
              throw new RollbackException(ErrorType.UPDATE_DENIED, "Plant may not set status to \"Accepted\".");
            }
          }
        }

        if (recordEvent == RecordEvent.BEFORE_INSERT) {
          if (globalCodeHasExistingResidualCostInStatus(record, "final__c")) {
            throw new RollbackException(ErrorType.INSERT_DENIED,
              "Cannot add a Residual Cost when a Final cost exists for the Global Code."
            );
          }
        }

        // there can be only a single Residual Cost record if the status is 'Final'
        if (costStatus.equals("final__c")) {
          if (globalCodeHasExistingResidualCosts(record)) {
            throw new RollbackException(ErrorType.OPERATION_DENIED,
              "Cannot add a Final Residual Cost if one or more Residual Costs already exist for the Global Code.");
          }
        }

      } // end of restrictions for plant

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

  private void checkInsertsAllowed(String pmfStatus) {
    if (!(pmfStatus.equals(PMFStatus.IN_PLANT_REVIEW) || pmfStatus.equals(PMFStatus.PLANT_REVIEW_COMPLETE))) {
      throw new RollbackException(ErrorType.OPERATION_DENIED, "Changes not allowed.");
    }
  }

  // this logic should be null-safe because the fields are required in the UI
  private void checkUpdatesAllowed(RecordTriggerContext context, String pmfStatus) {

    if (pmfStatus.equals(PMFStatus.IMPLEMENTATION_IN_PROGRESS) ||
        pmfStatus.equals(PMFStatus.COMPLETED) ||
        pmfStatus.equals(PMFStatus.REJECTED) ||
        pmfStatus.equals(PMFStatus.CANCELED)
    ) {
      if (costStatusChanged(context)) {
        throw new RollbackException(ErrorType.UPDATE_DENIED, "Cannot change Cost Status for this Residual Cost.");
      }

      if (proposedImplementationDateChanged(context)) {
          throw new RollbackException(ErrorType.UPDATE_DENIED, "Cannot change Proposed Implementation Date.");
      }

      if (estimatedResidualCostChanged(context)) {
          throw new RollbackException(ErrorType.UPDATE_DENIED, "Cannot change Estimated Residual Cost.");
      }
    }
  }

  private boolean costStatusChanged(RecordTriggerContext context) {
    Record newRecord = context.getRecordChanges().get(0).getNew();
    Record oldRecord = context.getRecordChanges().get(0).getOld();

    String newStatus = Util.getSinglePicklistValue(
      newRecord.getValue("cost_status__c", ValueType.PICKLIST_VALUES)
    );
    String oldStatus = Util.getSinglePicklistValue(
      oldRecord.getValue("cost_status__c", ValueType.PICKLIST_VALUES)
    );

    if (newStatus == null) {
      throw new RollbackException(ErrorType.UPDATE_DENIED, "Cost Status is required.");
    }
    return !newStatus.equals(oldStatus);
  }

  private boolean proposedImplementationDateChanged(RecordTriggerContext context) {
    Record newRecord = context.getRecordChanges().get(0).getNew();
    Record oldRecord = context.getRecordChanges().get(0).getOld();

    LocalDate newDate = newRecord.getValue("proposed_implementation_date__c", ValueType.DATE);
    LocalDate oldDate = oldRecord.getValue("proposed_implementation_date__c", ValueType.DATE);

    if (newDate == null) {
      throw new RollbackException(ErrorType.UPDATE_DENIED, "Proposed Implementation Date is required.");
    }
    return !newDate.equals(oldDate);
  }

  private boolean estimatedResidualCostChanged(RecordTriggerContext context) {
    Record newRecord = context.getRecordChanges().get(0).getNew();
    Record oldRecord = context.getRecordChanges().get(0).getOld();

    BigDecimal newCost = newRecord.getValue("estimated_residual_cost__c", ValueType.NUMBER);
    BigDecimal oldCost = oldRecord.getValue("estimated_residual_cost__c", ValueType.NUMBER);

    if (newCost == null) {
      throw new RollbackException(ErrorType.UPDATE_DENIED, "Estimated Residual Cost is required.");
    }
    return !newCost.equals(oldCost);
  }

    private boolean globalCodeHasExistingResidualCostInStatus(Record record, String status) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      String recordId = record.getValue("id", ValueType.STRING);
      String pmfGlobalCodeId = record.getValue("pmf_global_code_sku__c", ValueType.STRING);

      QueryResponse queryResponse = queryService.query(
        "select id from pmf_residual_cost__c " +
        " where pmf_global_code_sku__c = '"+pmfGlobalCodeId+"'" +
        "   and cost_status__c = '"+status+"'" +
        "   and id != '"+recordId+"'"
      );

      return queryResponse.getResultCount() > 0;
    }

    private boolean globalCodeHasExistingResidualCosts(Record record) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      String recordId = record.getValue("id", ValueType.STRING);
      String pmfGlobalCodeId = record.getValue("pmf_global_code_sku__c", ValueType.STRING);

      QueryResponse queryResponse = queryService.query(
        "select id from pmf_residual_cost__c " +
        " where pmf_global_code_sku__c = '"+pmfGlobalCodeId+"'" +
        "   and id != '"+recordId+"'"
      );

      return queryResponse.getResultCount() > 0;
    }

}
