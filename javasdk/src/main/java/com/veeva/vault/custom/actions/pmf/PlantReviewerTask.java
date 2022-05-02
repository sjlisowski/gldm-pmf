package com.veeva.vault.custom.actions.pmf;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.PMF;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.workflow.*;
import com.veeva.vault.sdk.api.job.JobParameters;
import com.veeva.vault.sdk.api.job.JobService;


/**
 * This module has two responsibilities:
 *
 *   1. Ensure that the Workflow Owner of the Implementation workflow is the PMF Requestor.  This is necessary because,
 *      for non-US PMF's, the Implementation Workflow is auto-started and initially owned by the System.
 *   2. Set the Plant Reviewer field on the PMF to the user who accepts the plant reviewers' task.
 */
@RecordWorkflowActionInfo(label="Plant Reviewer Task", object="pmf__c", stepTypes={WorkflowStepType.TASK})
public class PlantReviewerTask implements RecordWorkflowAction {

    public void execute(RecordWorkflowActionContext context) {

      WorkflowEvent taskEvent = context.getEvent();

      if (taskEvent == WorkflowEvent.TASK_AFTER_CREATE) {
        setWorkflowOwner(context);
      }
      else if (taskEvent == WorkflowEvent.TASK_AFTER_ASSIGN) {
        setPlantReviewerField(context);
      }
    }

    private void setWorkflowOwner(RecordWorkflowActionContext context) {

      LogService logService = ServiceLocator.locate(LogService.class);

      WorkflowInstance workflowInstance = context.getWorkflowInstance();
      Record pmfRecord = context.getRecords().get(0);

      String initiator = workflowInstance.getWorkflowInitiator();
      String pmfRequestor = pmfRecord.getValue("pmf_requestor__c", ValueType.STRING);

      if (initiator.equals(pmfRequestor)) {
        return;  // nothing to do here
      }

      String workflowId = workflowInstance.getId();
      String pmfNumber = pmfRecord.getValue("name__v", ValueType.STRING);

      JobService jobService = ServiceLocator.locate(JobService.class);
      JobParameters jobParameters = jobService.newJobParameters("pmf_set_implementation_workflow_owner__c");

      jobParameters.setValue("workflowId", workflowId);
      jobParameters.setValue("pmfRequestor", pmfRequestor);
      jobParameters.setValue("pmfNumber", pmfNumber);

      logService.info(
        "TASK_AFTER_CREATE: running Job set_implementation_workflow_owner__c for workflowId:",
        workflowId, "; pmfRequestor: ", pmfRequestor, "; pmfNumber: ", pmfNumber
      );

      jobService.runJob(jobParameters);

    }

    private void setPlantReviewerField(RecordWorkflowActionContext context) {

      RecordService recordService = ServiceLocator.locate(RecordService.class);

      String pmfId = context.getRecords().get(0).getValue("id", ValueType.STRING);
      WorkflowTaskInstance taskInstance = context.getTaskContext().getTaskChanges().get(0).getNew();
      String plantReviewerUserId = taskInstance.getAssigneeId();

      Record pmfRecord = recordService.newRecordWithId(PMF.ObjectName, pmfId );
      pmfRecord.setValue("plant_reviewer__c", plantReviewerUserId);

      recordService.batchSaveRecords(VaultCollections.asList(pmfRecord))
        .onErrors(batchOperationErrors -> {
          batchOperationErrors.stream().findFirst().ifPresent(error -> {
            String errMsg = error.getError().getMessage();
            throw new RollbackException(ErrorType.OPERATION_FAILED, errMsg);
          });
        })
        .execute();

    }

}