package com.veeva.vault.custom.actions.pmf;

import com.veeva.vault.sdk.api.action.RecordAction;
import com.veeva.vault.sdk.api.action.RecordActionContext;
import com.veeva.vault.sdk.api.action.RecordActionInfo;
import com.veeva.vault.sdk.api.action.Usage;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.job.JobParameters;
import com.veeva.vault.sdk.api.job.JobService;

/**
 * This class kicks off a Job that creates new Draft versions of the PMF's Impacted Artwork Documents.
 */

@RecordActionInfo(label="Up-Version Artwork Documents", object = "pmf__c",
  usages={Usage.LIFECYCLE_ENTRY_ACTION, Usage.USER_ACTION})
public class UpVersionArtworks implements RecordAction {

  public void execute(RecordActionContext recordActionContext) {

    JobService jobService = ServiceLocator.locate(JobService.class);
    JobParameters jobParameters = jobService.newJobParameters("pmf_upversion_artwork_documents__c");

    String pmfRecordId = recordActionContext.getRecords().get(0).getValue("id", ValueType.STRING);

    jobParameters.setValue("pmfRecordId", pmfRecordId);

    jobService.runJob(jobParameters);
  }

	public boolean isExecutable(RecordActionContext recordActionContext) {
	    return true;
	}
}