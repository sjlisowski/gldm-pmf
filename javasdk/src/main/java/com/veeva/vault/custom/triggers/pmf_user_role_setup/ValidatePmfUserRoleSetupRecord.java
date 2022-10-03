package com.veeva.vault.custom.triggers.pmf_user_role_setup;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.QueryUtil;
import com.veeva.vault.custom.udc.RegionCountryMap;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.util.Iterator;
import java.util.List;

/**
 * This trigger validates data entry for the PMF User Role Setup object for Inserts and Updates.
 */

@RecordTriggerInfo(
  object = "pmf_user_role_setup__c",
  events = {
    RecordEvent.BEFORE_INSERT,
    RecordEvent.BEFORE_UPDATE
  }
)
public class ValidatePmfUserRoleSetupRecord implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      if (recordTriggerContext.getRecordChanges().size() > 1) {
        return; // this trigger will not support bulk updates
      }

      Record newRecord = recordTriggerContext.getRecordChanges().get(0).getNew();

      String roleId = newRecord.getValue("role__c", ValueType.STRING);
      String roleName = Util.getRoleName(roleId);

      if (roleName.equals("pmf_plant_reviewer__c")) {
        validatePlantReviewer(newRecord);
      }
      else if (roleName.equals("regulatory__c")) {
        validateRegulatory(newRecord);
      }

    }

  private void validatePlantReviewer(Record record) {

    String logisticSite = record.getValue("logistic_site__c", ValueType.STRING);

    if (logisticSite == null) {
      throw new RollbackException(ErrorType.OPERATION_DENIED, "Logistic Site is required for Plant Reviewers.");
    }

    List<String> businessUnitList = record.getValue("business_unit__c", ValueType.PICKLIST_VALUES);
    String brand = record.getValue("brand__c", ValueType.STRING);
    String country = record.getValue("country__c", ValueType.STRING);
    String region = record.getValue("region__c", ValueType.STRING);

    if (
        businessUnitList != null ||
        brand != null ||
        country != null ||
        region != null
    ) {
      throw new RollbackException(ErrorType.OPERATION_DENIED,
        "Business Unit, Brand, Country and Region are not applicable to Plant Reviewers, and should be blank.");
    }

    String userId = record.getValue("user__c", ValueType.STRING);
    String roleId = record.getValue("role__c", ValueType.STRING);

    long count = QueryUtil.queryCount(
      "select id from pmf_user_role_setup__c" +
      " where user__c = '"+userId+"' and role__c = '"+roleId+"' and logistic_site__c = '"+logisticSite+"'"
    );
    if (count > 0) {
      throw new RollbackException(ErrorType.OPERATION_DENIED, "A duplicate record exists for this user.");
    }

  }

  private void validateRegulatory(Record record) {

    String logisticSite = record.getValue("logistic_site__c", ValueType.STRING);

    if (logisticSite != null) {
      throw new RollbackException(ErrorType.OPERATION_DENIED,
        "Logistic Site is not applicable for Regulatory Reviewers."
      );
    }

    String thisCountry = record.getValue("country__c", ValueType.STRING);
    String thisRegion = record.getValue("region__c", ValueType.STRING);

    if (thisCountry != null && thisRegion != null) {
      throw new RollbackException(ErrorType.OPERATION_DENIED,
        "Both Country and Region are not allowed on the same record."
      );
    }

    // test if any existing/other records for this user provide equivalent access...

    // thisRecordId is used to skip over the record being updated (on BEFORE UPDATE) (see query below)
    String thisRecordId = record.getValue("id", ValueType.STRING);
    if (thisRecordId == null) {
      thisRecordId = "x";  // use a dummy value for INSERTS
    }

    String thisBU = Util.getFirst(record.getValue("business_unit__c", ValueType.PICKLIST_VALUES));
    String thisBrand = record.getValue("brand__c", ValueType.STRING);
    String thisUserId = record.getValue("user__c", ValueType.STRING);
    String thisRoleId = record.getValue("role__c", ValueType.STRING);

    RegionCountryMap regionCountryMap = null;

    if (thisRegion != null) {
      regionCountryMap = new RegionCountryMap();
    }

    Iterator<QueryExecutionResult> iter = QueryUtil.query(
      "select brand__c, business_unit__c, country__c, region__c from pmf_user_role_setup__c" +
      " where user__c = '"+thisUserId+"' and role__c = '"+thisRoleId+"'" +
      "   and id != '"+thisRecordId+"'"
    ).streamResults().iterator();

    while (iter.hasNext()) {
      QueryExecutionResult queryResult = iter.next();
      String thatBU = Util.getFirst(queryResult.getValue("business_unit__c", ValueType.PICKLIST_VALUES));
      String thatBrand = queryResult.getValue("brand__c", ValueType.STRING);
      String thatCountry = queryResult.getValue("country__c", ValueType.STRING);
      String thatRegion = queryResult.getValue("region__c", ValueType.STRING);

      if (thatRegion != null) {
        if (regionCountryMap == null) {
          regionCountryMap = new RegionCountryMap();
        }
      }

      if (
        ( // 'equality' of brands
          (thisBrand == null || thatBrand == null) ||
          (Util.equals(thisBrand, thatBrand))
        )
      &&
        ( // 'equality' of business units
          (thisBU == null || thatBU == null) ||
          (Util.equals(thisBU, thatBU))
        )
      &&
        ( // 'equality' of country(/region)
          ((thisCountry == null && thatCountry == null) && (thisRegion == null && thatRegion == null)) ||

          (thisCountry != null && thatCountry != null && Util.equals(thisCountry, thatCountry)) ||
          (thisRegion != null & thatRegion != null && Util.equals(thisRegion, thatRegion)) ||

          (
            (thisCountry == null && thatCountry != null) &&
            (thisRegion == null || regionCountryMap.isCountryInRegion(thisRegion, thatCountry))
          ) ||
          (
            (thisCountry != null && thatCountry == null) &&
            (thatRegion == null || regionCountryMap.isCountryInRegion(thatRegion, thisCountry))
          ) ||

          (thisCountry == null && thisRegion == null && thatRegion != null) ||
          (thisRegion != null && thatRegion == null && thatCountry == null) ||
          (thisRegion != null && thatRegion != null && Util.equals(thisRegion, thatRegion))
        )
      ) {
        throw new RollbackException(ErrorType.OPERATION_DENIED,
          "An existing record for this user already provides this access."
        );
      }
    }

  } // end validateRegulatory()

}

