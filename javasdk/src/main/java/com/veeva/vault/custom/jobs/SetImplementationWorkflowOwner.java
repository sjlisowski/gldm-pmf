package com.veeva.vault.custom.jobs;

import com.veeva.vault.custom.udc.VaultAPI;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.job.*;

import java.util.List;

/**
 * This Job Processor changes the workflow owner to the PMF Requestor for auto-started Implementation workflows.
 *
 * This Job is started by a RecordWorkflowAction executed from the Plant Review task in the Implementation workflow.
 */

@JobInfo(adminConfigurable = true)
public class SetImplementationWorkflowOwner implements Job {

    public JobInputSupplier init(JobInitContext jobInitContext) {

      JobLogger logger = jobInitContext.getJobLogger();

      List<JobItem> jobItems = VaultCollections.newList();

      String workflowId = jobInitContext.getJobParameter("workflowId", JobParamValueType.STRING);
      String pmfRequestor = jobInitContext.getJobParameter("pmfRequestor", JobParamValueType.STRING);
      String pmfNumber = jobInitContext.getJobParameter("pmfNumber", JobParamValueType.STRING);

      logger.log(
        "Got job parameters: workflowId: "+workflowId+"; pmfRequestor: "+pmfRequestor+"; pmfNumber: "+pmfNumber
      );

      JobItem jobItem = jobInitContext.newJobItem();
      jobItem.setValue("workflowId", workflowId);
      jobItem.setValue("pmfRequestor", pmfRequestor);
      jobItem.setValue("pmfNumber", pmfNumber);
      jobItems.add(jobItem);
            
      return jobInitContext.newJobInput(jobItems);
    }

    public void process(JobProcessContext jobProcessContext) {

      JobLogger logger = jobProcessContext.getJobLogger();

      List<JobItem> jobItems = jobProcessContext.getCurrentTask().getItems();

      String errors = null;

      for (JobItem jobItem : jobItems) {

          String workflowId = jobItem.getValue("workflowId", JobValueType.STRING);
          String pmfRequestor = jobItem.getValue("pmfRequestor", JobValueType.STRING);
          String pmfNumber = jobItem.getValue("pmfNumber", JobValueType.STRING);

          logger.log(
            "Setting workflow owner for: workflowId: "+workflowId+"; pmfRequestor: "+pmfRequestor+"; pmfNumber: "+pmfNumber
          );

          VaultAPI vaultApi = new VaultAPI("pmf_local_connection__c");
          vaultApi.replaceWorklfowOwner(workflowId, pmfRequestor);

          if (vaultApi.failed()) {
            String errorType = vaultApi.getErrorType();
            String errorMsg = vaultApi.getErrorMessage();
            if (errors != null) {
              errors += "\n";
            }
            errors += "Error for workflowId '"+workflowId+"': ["+errorType+"] " + errorMsg;
          }

        }

        JobTask task = jobProcessContext.getCurrentTask();
        TaskOutput taskOutput = task.getTaskOutput();

        if (errors == null) {
            taskOutput.setState(TaskState.SUCCESS);
            logger.log("Task successful");
        } else {
            taskOutput.setState(TaskState.ERRORS_ENCOUNTERED);
            taskOutput.setValue("errors", errors);
            logger.log("Task unsuccessful");
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
                logger.log(task.getTaskId() + " failed with error message " +
                  taskOutput.getValue("errors", JobValueType.STRING)
                );
            }
        }
    }
}
