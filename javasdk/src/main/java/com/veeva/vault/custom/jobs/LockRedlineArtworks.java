package com.veeva.vault.custom.jobs;

import com.veeva.vault.custom.udc.VaultAPI;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.job.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.List;

  /*
   * This Job Process puts a PMF's Redline Artwork Documents into the "Locked" status.
   *
   * Chunk Size for this job MUST be set to 1, so that an exception on one Document won't
   * terminate the Job completely.
   */
  
  @JobInfo(adminConfigurable = true)
  public class LockRedlineArtworks implements Job {

    private static final String REDLINE_LOCK_ACTION_LABEL = "Lock";
    private static final String REDLINE_STATUS_LOCKED = "Locked";

    public JobInputSupplier init(JobInitContext jobInitContext) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);
      JobLogger logger = jobInitContext.getJobLogger();

      String pmfRecordId = jobInitContext.getJobParameter("pmfRecordId", JobParamValueType.STRING);
      logger.log("Processing PMF " + pmfRecordId);

      List<JobItem> jobItems = VaultCollections.newList();

      String query =
        "select redline_artwork_document__c " +
        "  from pmf_redline_artwork_document__c " +
        " where pmf__c = '"+pmfRecordId+"'";
      logger.log("Executing VQL query: " + query);

      QueryResponse queryResponse = queryService.query(query);
      logger.log("VQL result count: " + queryResponse.getResultCount());

      Iterator<QueryResult> iter = queryResponse.streamResults().iterator();

      while (iter.hasNext()) {
        QueryResult qr = iter.next();
        String redlineVersionId = qr.getValue("redline_artwork_document__c", ValueType.STRING);
        JobItem jobItem = jobInitContext.newJobItem();
        jobItem.setValue("redlineVersionId", redlineVersionId);
        jobItem.setValue("pmfRecordId",pmfRecordId);
        jobItems.add(jobItem);
        logger.log("Added job item for redline artwork version " + redlineVersionId);
      }

      return jobInitContext.newJobInput(jobItems);
    }

    public void process(JobProcessContext jobProcessContext) {
      RecordService recordService = ServiceLocator.locate(RecordService.class);
      JobLogger logger = jobProcessContext.getJobLogger();

      List<JobItem> items = jobProcessContext.getCurrentTask().getItems();

      String error = null;

      for (JobItem jobItem : items) {
        String redlineVersionId = jobItem.getValue("redlineVersionId", JobValueType.STRING);
        logger.log("Processing redline document " + redlineVersionId);
        if (!isAlreadyLocked(redlineVersionId)) {
          VaultAPI vaultApi = new VaultAPI("pmf_local_connection__c", logger);
          vaultApi.initiateDocumentUserAction(redlineVersionId, "lock__c");
          if (vaultApi.failed()) {
            error = "Failed to move redline document redlineVersionId to the Locked status due to: " +
              vaultApi.getErrorMessage();
          } else {
            logger.log("Successfully locked Redline Artwork document " + redlineVersionId);
          }
        } else {
          logger.log("Redline Artwork Document '"+redlineVersionId+"' is already locked");
        }
      }

       JobTask task = jobProcessContext.getCurrentTask();
       TaskOutput taskOutput = task.getTaskOutput();

       if (error == null) {
         taskOutput.setState(TaskState.SUCCESS);
         logger.log("Task successful");
       } else {
          taskOutput.setState(TaskState.ERRORS_ENCOUNTERED);
          taskOutput.setValue("errorMessage", error);
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
       logger.log("completeWithError: "+result.getNumberFailedTasks()+" tasks failed out of " + result.getNumberTasks());

       List<JobTask> tasks = jobCompletionContext.getTasks();
       for (JobTask task : tasks) {
         TaskOutput taskOutput = task.getTaskOutput();
         if (TaskState.ERRORS_ENCOUNTERED.equals(taskOutput.getState())) {
           logger.log(task.getTaskId() + " failed with error message " + taskOutput.getValue("errorMessage", JobValueType.STRING));
         }
       }
    }

    private boolean isAlreadyLocked(String redlineVersionId) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      QueryResponse queryResponse = queryService.query(
        "select id from documents where version_id = '"+redlineVersionId+"' and status__v = '"+REDLINE_STATUS_LOCKED+"'"
      );

      return queryResponse.getResultCount() > 0;
    }

  }