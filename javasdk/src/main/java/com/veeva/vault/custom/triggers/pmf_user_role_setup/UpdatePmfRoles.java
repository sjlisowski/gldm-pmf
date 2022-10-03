package com.veeva.vault.custom.triggers.pmf_user_role_setup;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.PMFStatus;
import com.veeva.vault.custom.udc.QueryUtil;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;
import com.veeva.vault.sdk.api.role.RecordRoleService;
import com.veeva.vault.sdk.api.role.RecordRoleUpdate;

import java.util.Iterator;
import java.util.List;

/**
 *
 * This trigger updates Regulatory and Plant Reviewer roles on active PMF's.
 *
 */

@RecordTriggerInfo(
  object = "pmf_user_role_setup__c",
  events = {
    RecordEvent.AFTER_INSERT,
    RecordEvent.AFTER_UPDATE,
    RecordEvent.AFTER_DELETE
  }
)
public class UpdatePmfRoles implements RecordTrigger {

  private static final int ROLE_ACTION_ADD = 1;
  private static final int ROLE_ACTION_REMOVE = 2;

  public void execute(RecordTriggerContext recordTriggerContext) {

    if (recordTriggerContext.getRecordChanges().size() > 1) {
      return;  // for single record changes only
    }

    RecordChange recordChange = recordTriggerContext.getRecordChanges().get(0);
    RecordEvent recordEvent = recordTriggerContext.getRecordEvent();

    if (recordEvent == RecordEvent.AFTER_DELETE) {
      updateRoleForPmfs(recordChange.getOld(), ROLE_ACTION_REMOVE);
    }
    else if (recordEvent == RecordEvent.AFTER_UPDATE) {
      updateRoleForPmfs(recordChange.getOld(), ROLE_ACTION_REMOVE);
      updateRoleForPmfs(recordChange.getNew(), ROLE_ACTION_ADD);
    }
    else {
      updateRoleForPmfs(recordChange.getNew(), ROLE_ACTION_ADD);
    }

    return;
  } // end execute()

  /*
     Locate all of the PMF's for which the user should be added or removed to/from the role reflected
     by the updated PMF User Role Setup record, and add/remove the User to/from the Role for each PMF
     based on record trigger event.
  */
  private void updateRoleForPmfs(Record record, int roleAction) {

    RecordService recordService = ServiceLocator.locate(RecordService.class);
    RecordRoleService recordRoleService = ServiceLocator.locate(RecordRoleService.class);
    LogService logService = ServiceLocator.locate(LogService.class);

    String userId = record.getValue("user__c", ValueType.STRING);
    String roleId = record.getValue("role__c", ValueType.STRING);
    List<String> businessUnitList = record.getValue("business_unit__c", ValueType.PICKLIST_VALUES);
    String businessUnit = businessUnitList != null ? businessUnitList.get(0) : null;
    String brand = record.getValue("brand__c", ValueType.STRING);
    String country = record.getValue("country__c", ValueType.STRING);
    String region = record.getValue("region__c", ValueType.STRING);
    String logisticSite = record.getValue("logistic_site__c", ValueType.STRING);

    String roleName = Util.getRoleName(roleId);

    logService.info("Starting role action: {} for role: {}", roleAction==ROLE_ACTION_ADD ? "add" : "remove", roleName);

    List<String> applicablePmfIds = getApplicablePmfIds(roleName, businessUnit, brand, country, region, logisticSite);

    logService.info("Found {} PMFs", applicablePmfIds.size());

    List<RecordRoleUpdate> roleUpdates = VaultCollections.newList();

    for (String pmfId : applicablePmfIds) {
      logService.info("PMF Id: {}", pmfId);
      Record pmfRecord = recordService.newRecordWithId("pmf__c", pmfId);
      RecordRoleUpdate recordRoleUpdate = recordRoleService.newRecordRoleUpdate(roleName, pmfRecord);
      if (roleAction == ROLE_ACTION_ADD) {
        recordRoleUpdate.addUsers(VaultCollections.asList(userId));
      } else {
        recordRoleUpdate.removeUsers(VaultCollections.asList(userId));
      }
      roleUpdates.add(recordRoleUpdate);
    }

    recordRoleService.batchUpdateRecordRoles(roleUpdates)
      .rollbackOnErrors()
      .execute();

  }

  /*
     Return a list of Record IDs for PMF records that are applicable to the parameters.
  */
  private List<String> getApplicablePmfIds(
    String roleName,
    String businessUnit,
    String brand,
    String country,
    String region,
    String logisticSite
  ) {
    LogService logService = ServiceLocator.locate(LogService.class);

    StringBuilder query = new StringBuilder();
    query.append("select id from pmf__c");

    List<String> statuses = VaultCollections.newList(); // to filter for PMF's in specific statuses
    statuses.add(PMFStatus.DRAFT);
    statuses.add(PMFStatus.IN_REVIEW);

    if (roleName.equals("regulatory__c")) {

      String USCountryId = Util.getUSCountryId();

      if (country == null) {
        query.append(" where requestor_country__c != '"+USCountryId+"'");
        if (region != null) {
          query.append(" and requestor_country__c contains " + Util.vqlContains(Util.getCountriesInRegion(region)));
        }
      } else {
        query.append(" where requestor_country__c = '"+country+"'");
      }

      if (brand != null) {
        query.append(" and brand__c = '"+brand+"'");
      }

      if (businessUnit != null) {
        query.append(" and business_unit__c = '"+businessUnit+"'");
      }

      query.append(" and state__v contains " + Util.vqlContains(statuses));

    } else if (roleName.equals("pmf_plant_reviewer__c")) {

      statuses.add(PMFStatus.AMEND_AND_RESUBMIT);
      statuses.add(PMFStatus.REVIEW_COMPLETE);
      statuses.add(PMFStatus.APPROVED);
      statuses.add(PMFStatus.IN_PLANT_REVIEW);

      query
        .append(" where logistic_site__c = '"+logisticSite+"'")
        .append(" and state__v contains " + Util.vqlContains(statuses));

    } else {
      throw new RollbackException(ErrorType.OPERATION_FAILED, "Invalid role name: " + roleName);
    }

    logService.info(query.toString());

    Iterator<QueryExecutionResult> iter = QueryUtil.query(query.toString()).streamResults().iterator();
    List<String> pmfList = VaultCollections.newList();

    while (iter.hasNext()) {
      pmfList.add(iter.next().getValue("id", ValueType.STRING));
    }

    return pmfList;
  }

//    public void execute(RecordTriggerContext recordTriggerContext) {
//
//      JobService jobService = ServiceLocator.locate(JobService.class);
//      JobParameters jobParameters = jobService.newJobParameters("pmf_update_roles__c");
//
//      Set<String> roleNames = VaultCollections.newSet();
//      RecordEvent recordEvent = recordTriggerContext.getRecordEvent();
//
//      for (RecordChange recordChange : recordTriggerContext.getRecordChanges()) {
//
//        String roleId;
//
//        if (recordEvent == RecordEvent.AFTER_DELETE) {
//          roleId = recordChange.getOld().getValue("role__c", ValueType.STRING);
//        } else {
//          roleId = recordChange.getNew().getValue("role__c", ValueType.STRING);
//        }
//
//        roleNames.add(
//          Util.getRecordValue("application_role__v", "api_name__v", roleId, ValueType.STRING)
//        );
//
//      }
//
//      String concatenatedRoleNames = String.join("|", roleNames);
//      jobParameters.setValue("concatenatedRoleNames", concatenatedRoleNames);
//
//      jobService.runJob(jobParameters);
//
//      return;
//
//    } // end execute()

//    private static boolean isRegulatoryRole(RecordTriggerContext recordTriggerContext) {
//      Record record = recordTriggerContext.getRecordChanges().iterator().next().getNew();
//      String roleId = record.getValue("role__c", ValueType.STRING);
//      return roleId.equals(Util.getRoleId("regulatory__c"));
//    }
}

