package com.veeva.vault.custom.jobs;

import com.veeva.vault.custom.udc.VaultAPI;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.job.*;

import java.util.List;

/**
 * StartPmfDraftWorkflow
 *
 * Start the "PMF Draft" workflow for a newly created Draft of an approved Artwork document.
 *
 * This Job is executed separately for individual documents.
 */

@JobInfo(adminConfigurable = true)
public class StartPmfDraftWorkflow implements Job {

    // These strings will be reference by the action that executes this Job Processor...
    public static final String JOB_PARM_DOC_ID = "docId";
    public static final String JOB_PARM_DOC_OWNER = "docOwner";

    private static final String ERROR_MSG = "errMsg";

    public JobInputSupplier init(JobInitContext jobInitContext) {

      JobLogger logger = jobInitContext.getJobLogger();

      List<JobItem> jobItems = VaultCollections.newList();

      String docId = jobInitContext.getJobParameter(JOB_PARM_DOC_ID, JobParamValueType.STRING);
      String docOwner = jobInitContext.getJobParameter(JOB_PARM_DOC_OWNER, JobParamValueType.STRING);

      JobItem jobItem = jobInitContext.newJobItem();
      jobItem.setValue(JOB_PARM_DOC_ID, docId);
      jobItem.setValue(JOB_PARM_DOC_OWNER, docOwner);
      jobItems.add(jobItem);
            
      logger.log("Added Job Input Item for docId '"+docId+"' with docOwner '"+docOwner+"'");

      return jobInitContext.newJobInput(jobItems);
    }

    public void process(JobProcessContext jobProcessContext) {

      JobLogger logger = jobProcessContext.getJobLogger();

      JobTask task = jobProcessContext.getCurrentTask();
      TaskOutput taskOutput = task.getTaskOutput();

      JobItem jobItem = jobProcessContext.getCurrentTask().getItems().get(0);

      String docId = jobItem.getValue(JOB_PARM_DOC_ID, JobValueType.STRING);
      String docOwner = jobItem.getValue(JOB_PARM_DOC_OWNER, JobValueType.STRING);

      logger.log("Starting 'PMF Draft' workflow for "+docId);

      VaultAPI vaultAPI = new VaultAPI("pmf_local_connection__c", logger);
      vaultAPI
        .addParam("documents__sys", docId)
        .addParam("part_document_owner__c", "user:"+docOwner)
        .addParam("description__sys", "PMF Draft")
        .initiateDocumentWorklow("pmf_draft__c");

      if (vaultAPI.failed()) {
        String errorType = vaultAPI.getErrorType();
        String errorMsg = vaultAPI.getErrorMessage();
        String fullMsg = "Failed to start workflow 'PMF Draft' for " + docId + ": ["+errorType+"] " + errorMsg;
        logger.log(fullMsg);
        taskOutput.setState(TaskState.ERRORS_ENCOUNTERED);
        taskOutput.setValue(ERROR_MSG, fullMsg);
      } else {
        logger.log("Workflow 'PMF Draft' successfully started for " + docId);
        taskOutput.setState(TaskState.SUCCESS);
      }
    }

    public void completeWithSuccess(JobCompletionContext jobCompletionContext) {
        JobLogger logger = jobCompletionContext.getJobLogger();
        logger.log("All tasks completed successfully");
    }

    public void completeWithError(JobCompletionContext jobCompletionContext) {
        JobResult result = jobCompletionContext.getJobResult();

        JobLogger logger = jobCompletionContext.getJobLogger();
        logger.log("completeWithError: " + result.getNumberFailedTasks() + "tasks failed out of " + result.getNumberTasks());

        List<JobTask> tasks = jobCompletionContext.getTasks();
        for (JobTask task : tasks) {
            TaskOutput taskOutput = task.getTaskOutput();
            if (TaskState.ERRORS_ENCOUNTERED.equals(taskOutput.getState())) {
                logger.log(task.getTaskId() + " failed with error message " + taskOutput.getValue(ERROR_MSG, JobValueType.STRING));
            }
        }
    }
}
