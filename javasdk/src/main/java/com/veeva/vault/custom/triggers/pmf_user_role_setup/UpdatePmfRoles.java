package com.veeva.vault.custom.triggers.pmf_user_role_setup;

import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.job.JobParameters;
import com.veeva.vault.sdk.api.job.JobService;

import java.util.Set;

/**
 *
 * This trigger updates Regulatory and Plant Reviewer roles on active PMF's.
 *
 */

@RecordTriggerInfo(object = "pmf_user_role_setup__c",
  events = {RecordEvent.AFTER_INSERT, RecordEvent.AFTER_UPDATE, RecordEvent.AFTER_DELETE})
public class UpdatePmfRoles implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      JobService jobService = ServiceLocator.locate(JobService.class);
      JobParameters jobParameters = jobService.newJobParameters("pmf_update_roles__c");

      Set<String> roleNames = VaultCollections.newSet();
      RecordEvent recordEvent = recordTriggerContext.getRecordEvent();

      for (RecordChange recordChange : recordTriggerContext.getRecordChanges()) {

        String roleId;

        if (recordEvent == RecordEvent.AFTER_DELETE) {
          roleId = recordChange.getOld().getValue("role__c", ValueType.STRING);
        } else {
          roleId = recordChange.getNew().getValue("role__c", ValueType.STRING);
        }

        roleNames.add(
          Util.getRecordValue("application_role__v", "api_name__v", roleId, ValueType.STRING)
        );

      }

      String concatenatedRoleNames = String.join("|", roleNames);
      jobParameters.setValue("concatenatedRoleNames", concatenatedRoleNames);

      jobService.runJob(jobParameters);

      return;

    } // end execute()

    private static boolean isRegulatoryRole(RecordTriggerContext recordTriggerContext) {
      Record record = recordTriggerContext.getRecordChanges().iterator().next().getNew();
      String roleId = record.getValue("role__c", ValueType.STRING);
      return roleId.equals(Util.getRoleId("regulatory__c"));
    }
}

