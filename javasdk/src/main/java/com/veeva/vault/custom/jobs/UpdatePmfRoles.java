package com.veeva.vault.custom.jobs;

import com.veeva.vault.custom.udc.PMF;
import com.veeva.vault.custom.udc.PMFStatus;
import com.veeva.vault.custom.udc.PmfUsers;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.ReadRecordsResponse;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.job.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 *
 * This job updates Regulatory and Plant Reviewer roles on active PMF's based on changes
 * in PMF User Role Setup.
 *
 */
  
  @JobInfo(adminConfigurable = true)
  public class UpdatePmfRoles implements Job {

    public JobInputSupplier init(JobInitContext jobInitContext) {

      JobLogger logger = jobInitContext.getJobLogger();

      String concatenatedRoleNames = jobInitContext.getJobParameter("concatenatedRoleNames", JobParamValueType.STRING);

      JobItem jobItem = jobInitContext.newJobItem();
      jobItem.setValue("concatenatedRoleNames", concatenatedRoleNames);

      return jobInitContext.newJobInput(VaultCollections.asList(jobItem));
    }

    public void process(JobProcessContext jobProcessContext) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);
      RecordService recordService = ServiceLocator.locate(RecordService.class);
      JobLogger logger = jobProcessContext.getJobLogger();

      JobItem item = jobProcessContext.getCurrentTask().getItems().get(0);

      String concatenatedRoleNames = item.getValue("concatenatedRoleNames", JobValueType.STRING);
      boolean containsRegulatoryRole = concatenatedRoleNames.contains("regulatory__c");
      boolean containsPlantReviewerRole = concatenatedRoleNames.contains("pmf_plant_reviewer__c");

      List<String> states = VaultCollections.newList();

      states.add(PMFStatus.DRAFT);
      states.add(PMFStatus.IN_REVIEW);

      if (containsPlantReviewerRole) {
        states.add(PMFStatus.AMEND_AND_RESUBMIT);
        states.add(PMFStatus.REVIEW_COMPLETE);
        states.add(PMFStatus.APPROVED);
        states.add(PMFStatus.IN_PLANT_REVIEW);
      }

      QueryResponse queryResponse = queryService.query(
        "select id from pmf__c where state__v contains " + Util.vqlContains(states)
      );

      if (queryResponse.getResultCount() > 0) {

        List<Record> records = VaultCollections.newList();

        Iterator<QueryResult> iter = queryResponse.streamResults().iterator();
        while (iter.hasNext()) {
          QueryResult qr = iter.next();
          records.add(
            recordService.newRecordWithId("pmf__c", qr.getValue("id", ValueType.STRING))
          );
        }

        ReadRecordsResponse recordsResponse = recordService.readRecords(records);
        Collection<Record> readRecords = recordsResponse.getRecords().values();

        PmfUsers pmfUsers = new PmfUsers();

        for (Record record : readRecords) {
          String pmfName = record.getValue("name__v", ValueType.STRING);
          if (containsRegulatoryRole) {
            logger.log("Updating Regulatory role for " + pmfName);
            PMF.updateRegulatoryRole(record, pmfUsers);
          }
          if (containsPlantReviewerRole) {
            logger.log("Updating Plant Reviewer role for " + pmfName);
            PMF.updatePlantReviewerRole(record, pmfUsers);
          }
        }

      }

      JobTask task = jobProcessContext.getCurrentTask();
      TaskOutput taskOutput = task.getTaskOutput();
      taskOutput.setState(TaskState.SUCCESS);

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
              logger.log(task.getTaskId() + " failed with error message " + taskOutput.getValue("firstError", JobValueType.STRING));
           }
       }
    }
  }