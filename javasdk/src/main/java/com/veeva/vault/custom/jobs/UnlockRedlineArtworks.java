package com.veeva.vault.custom.jobs;

import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.custom.udc.VaultAPI;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.StringUtils;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.job.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.List;

/**
 * Set Redlined Artwork documents to the "Open" (draft__c) status
 *  for all Redline Artwork documents for a given PMF.
 */

@JobInfo(adminConfigurable = true)
public class UnlockRedlineArtworks implements Job {

    // This constant is public so it can be referenced by the action that executes the job.
    public static final String JOB_PARAM_PMF_RECORD_ID = "jobParamPmfId";

    private static final String REDLINE_VERSION_ID = "redlineVersionId";
    private static final String TASK_ERROR_MSG = "taskErrorMsg";

    public JobInputSupplier init(JobInitContext jobInitContext) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);
      QueryResponse queryResponse;
      Iterator<QueryResult> queryResultIterator;
      String query;

      JobLogger logger = jobInitContext.getJobLogger();

      List<JobItem> jobItems = VaultCollections.newList();

      String pmfRecordId = jobInitContext.getJobParameter(JOB_PARAM_PMF_RECORD_ID, JobParamValueType.STRING);

      logger.log("Processing locked Redline Artwork documents for PMF " + pmfRecordId);

      query =
        "select redline_artwork_document__c" +
        "  from pmf_redline_artwork_document__c" +
        " where pmf__c = '"+pmfRecordId+"'";
      logger.log(StringUtils.replaceAll(query, "\n", ""));
      queryResponse = queryService.query(query);
      queryResultIterator = queryResponse.streamResults().iterator();
      List<String> redlineDocVersionIds = VaultCollections.newList();

      while (queryResultIterator.hasNext()) {
        QueryResult queryResult = queryResultIterator.next();
        String redlineDocVersionId = queryResult.getValue("redline_artwork_document__c", ValueType.STRING);
        redlineDocVersionIds.add(redlineDocVersionId);
      }

      logger.log("Found "+redlineDocVersionIds.size()+" Redline Artwork document(s)");

      if (redlineDocVersionIds.size() > 0) {

        logger.log("Looking for locked Redline Artwork documents...");
        query =
          "select version_id" +
          "  from documents " +
          " where version_id contains " + Util.vqlContains(redlineDocVersionIds) +
          "   and toName(status__v) = 'locked__c'";
        logger.log(StringUtils.replaceAll(query, "\n", ""));
        queryResponse = queryService.query(query);
        queryResultIterator = queryResponse.streamResults().iterator();

        while (queryResultIterator.hasNext()) {
          QueryResult queryResult = queryResultIterator.next();
          String redlineDocVersionId = queryResult.getValue("version_id", ValueType.STRING);
          JobItem jobItem = jobInitContext.newJobItem();
          jobItem.setValue(REDLINE_VERSION_ID, redlineDocVersionId);
          jobItems.add(jobItem);
          logger.log("Added locked redline document "+redlineDocVersionId+" to task list");
        }

        if (jobItems.size() == 0) {
          logger.log("No locked Redline Artwork documents found for PMF " + pmfRecordId);
        }

      }

      return jobInitContext.newJobInput(jobItems);
    }

    public void process(JobProcessContext jobProcessContext) {

      JobLogger logger = jobProcessContext.getJobLogger();

      List<JobItem> jobItems = jobProcessContext.getCurrentTask().getItems();

      VaultAPI vaultAPI = new VaultAPI("pmf_local_connection__c", logger);

      int errorCount = 0;

      for (JobItem jobItem : jobItems) {
        String redlineDocVersionId = jobItem.getValue(REDLINE_VERSION_ID, JobValueType.STRING);
        logger.log("Processing locked redline document " + redlineDocVersionId);
        vaultAPI.initiateDocumentUserAction(redlineDocVersionId, "open__c");
        if (vaultAPI.failed()) {
          String errType = vaultAPI.getErrorType();
          String errMsg = vaultAPI.getErrorMessage();;
          logger.log("Failed to unlock redline "+redlineDocVersionId+" due to: ["+errType+"] "+errMsg);
          errorCount++;
        }
      }

      JobTask task = jobProcessContext.getCurrentTask();
      TaskOutput taskOutput = task.getTaskOutput();

      if (errorCount == 0) {
        taskOutput.setState(TaskState.SUCCESS);
        logger.log("Task successful");
      } else {
        taskOutput.setState(TaskState.ERRORS_ENCOUNTERED);
        logger.log("Errors occurred");
      }

    }

    public void completeWithSuccess(JobCompletionContext jobCompletionContext) {
      JobLogger logger = jobCompletionContext.getJobLogger();
      logger.log("All tasks completed successfully");
    }

    public void completeWithError(JobCompletionContext jobCompletionContext) {
      JobResult result = jobCompletionContext.getJobResult();
      JobLogger logger = jobCompletionContext.getJobLogger();
      logger.log("completeWithError: "+result.getNumberFailedTasks()+"tasks failed out of "+result.getNumberTasks());

    }
}
