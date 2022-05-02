package com.veeva.vault.custom.triggers.pmf;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.group.Group;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryService;
import com.veeva.vault.sdk.api.role.*;

import java.util.List;

/**
 *
 * This trigger enforces the rule that only a single user can be in the PMF Lifecycle's Owner Role:
 *    the PMF Requestor.
 */

@RecordRoleTriggerInfo(object = "pmf__c", events = {RecordRoleEvent.BEFORE}, order = TriggerOrder.NUMBER_1)
public class ValidateRoleChange implements RecordRoleTrigger {

  public void execute(RecordRoleTriggerContext context) {

    List<RecordRoleChange> roleChanges = context.getRecordRoleChanges();

    for (RecordRoleChange roleChange : roleChanges) {

      String roleName = roleChange.getRecordRole().getRole().getRoleName();

      if (roleName.equals("owner__v")) {

        List<Group> groupsAdded = roleChange.getGroupsAdded();
        if (groupsAdded.size() > 0) {
          throw new RollbackException(ErrorType.UPDATE_DENIED, "Group not allowed in Owner role");
        }

        String pmfRequestorId = pmfRequestorId(roleChange);

        List<String> usersAdded = roleChange.getUsersAdded();
        if (usersAdded.size() > 0) {
          if (usersAdded.size() > 1 || !usersAdded.get(0).equals(pmfRequestorId)) {
            throw new RollbackException(ErrorType.UPDATE_DENIED, "Owner must be the PMF Requestor");
          }
        }

        List<String> usersRemoved = roleChange.getUsersRemoved();
        if (usersRemoved.size() > 0) {
          if (usersRemoved.contains(pmfRequestorId)) {
            throw new RollbackException(ErrorType.UPDATE_DENIED, "Cannot remove PMF Requestor from Owner role");
          }
        }

      } // end if

    } // end for

  }

  private String pmfRequestorId(RecordRoleChange roleChange) {

    QueryService queryService = ServiceLocator.locate(QueryService.class);
    String pmfId = roleChange.getRecordRole().getRecord().getValue("id", ValueType.STRING);

    QueryResponse queryResponse = queryService.query(
      "select pmf_requestor__c from pmf__c where id = '"+pmfId+"'"
    );

    return queryResponse.streamResults().findFirst().get().getValue("pmf_requestor__c", ValueType.STRING);
  }

}