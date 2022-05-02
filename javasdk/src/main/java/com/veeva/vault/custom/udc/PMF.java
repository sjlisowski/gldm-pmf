package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.ReadRecordsResponse;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.group.Group;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;
import com.veeva.vault.sdk.api.role.GetRecordRolesResponse;
import com.veeva.vault.sdk.api.role.RecordRole;
import com.veeva.vault.sdk.api.role.RecordRoleService;
import com.veeva.vault.sdk.api.role.RecordRoleUpdate;

import java.util.Iterator;
import java.util.List;

/**
 * This UDC contains static methods to support processing related directly to PMF object records.
 */

/*
  Static methods in this class:

    getObjectTypeName - Return the API name of a PMF record's Object Type.
    getURL - Return a Vault UI URL to the PMF Object record.
    getRequestor - Return the User ID of the PMF's Requestor.
    getStatus - Return the PMF record's current lifecycle state.
    getRecord - Return the fully populated Record object for the PMF.
    updateLogisticSite - Update the Logistic Site field on the PMF object record.
    updateGlobalCodesAndCountriesDisplay - Update the Global Codes and Impacted Countries display fields.
    updateOwnerRole - Update the Owner role (owner__v) on a PMF record, replacing the current user with a new user.
    updateRegulatoryRole - Populate the Regulatory role on the PMF object record.
    updatePlantReviewerRole - Populate the Plant Reviewer role on the PMF object record.
    setArtworkPackagingNumbers - Populate the "Packaging Number(s)" fields from the impacted Artworks
    isSafetyVariation - return true if the PMF is a Safety Variation, otherwise return false
 */

@UserDefinedClassInfo
public class PMF {

   public static final String ObjectName = "pmf__c";

   /**
    * Return the API name of a PMF record's Object Type.
    * @param pmfRecord - Record object
    * @return String - api name of the object type
    */
    public static String getObjectTypeName(Record pmfRecord) {
      return Util.getTypeName(pmfRecord);
    }

   /**
     * Return the API name of a PMF record's Object Type.
     * @param pmfId - String - PMF Object Record ID
     * @return String - api name of the object type
    */
    public static String getObjectTypeName(String pmfId) {
      return Util.getObjectTypeName("pmf__c", pmfId);
    }

    /**
     * Return a Vault UI URL to the PMF Object record.
     * @param pmfRecordId
     * @return
     */
    public static String getURL(String pmfRecordId) {
      return Util.getObjectRecordURL(Parameters.getVaultDomain(), "pmf__c", pmfRecordId);
    }

    /**
     * Return the User ID of the PMF's Requestor.
     * @param pmfRecordId
     * @return
     */
    public static String getRequestor(String pmfRecordId) {
      QueryService queryService = ServiceLocator.locate(QueryService.class);
      String userId = null;
      QueryResponse queryResponse = queryService.query(
        "select pmf_requestor__c from pmf__c where id = '"+pmfRecordId+"'"
      );
      if (queryResponse.getResultCount() > 0) {
        userId = queryResponse.streamResults().findFirst().get().getValue("pmf_requestor__c", ValueType.STRING);
      }
      return userId;
    }

    /**
     * Return the PMF record's current lifecycle state.
     * @param pmfRecordId - String. PMF record ID
     * @return String
     */
    public static String getStatus(String pmfRecordId) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      String lifecycleState = queryService.query(
        "select state__v from pmf__c where id = '"+pmfRecordId+"'"
      )
        .streamResults()
        .iterator()
        .next()
        .getValue("state__v", ValueType.STRING);

      return lifecycleState;
    }

    /**
     * Return the fully populated Record object for the PMF.
     * @param pmfRecordId
     * @return
     */
    public static Record getRecord(String pmfRecordId) {
      RecordService recordService = ServiceLocator.locate(RecordService.class);
      Record record = recordService.newRecordWithId("pmf__c", pmfRecordId);
      ReadRecordsResponse response = recordService.readRecords(VaultCollections.asList(record));
      return response.getRecords().get(pmfRecordId);
    }

    /**
     * Update the Logistic Site field on the PMF.
     * @param pmfId - String. PMF Object record id
     * @param logisticSiteId - String Logistic Site object record id
     */
    public static void updateLogisticSite(String pmfId, String logisticSiteId) {

      RecordService rs = ServiceLocator.locate(RecordService.class);

      Record pmfRecord = rs.newRecordWithId("pmf__c", pmfId);
      pmfRecord.setValue("logistic_site__c", logisticSiteId);

      rs.batchSaveRecords(VaultCollections.asList(pmfRecord))
        .onErrors(errors -> {
          errors.stream().findFirst().ifPresent(error -> {
            String errMsg = error.getError().getMessage();
            throw new RollbackException(ErrorType.OPERATION_FAILED,
              "Failed to update the Logistic Site on the PMF due to: " + errMsg + "\n" + "PMF ID: " + pmfId);
          });
        })
        .execute();
    }

    /**
     * Update the Global Codes and Impacted Countries display fields with comma-delimited lists.
     *
     * @param pmfRecordId
     */
    public static void updateGlobalCodesAndCountriesDisplay(String pmfRecordId) {

      RecordService recordService = ServiceLocator.locate(RecordService.class);

      String globalCodes = getGlobalCodesDisplay(pmfRecordId);
      String impactedCountries = getImpactedCountriesDisplay(pmfRecordId);

      Record pmfRecord = recordService.newRecordWithId("pmf__c", pmfRecordId);
      pmfRecord.setValue("global_codes__c", globalCodes);
      pmfRecord.setValue("impacted_countries__c", impactedCountries);

      recordService.batchSaveRecords(VaultCollections.asList(pmfRecord))
        .onErrors(errors -> {
          errors.stream().findFirst().ifPresent(error -> {
            String errMsg = error.getError().getMessage();
            throw new RollbackException(ErrorType.OPERATION_FAILED,
              "Failed to update the Global Codes field on the PMF due to: " + errMsg + "\n" + "PMF ID: " + pmfRecordId);
          });
        })
        .execute();

    }

  /*
   * Get a comma-delimited list of Impacted Global Codes
   */
  private static String getGlobalCodesDisplay(String pmfRecordId) {

    final int MAX_FIELD_LENGTH = 5000;
    final String DELIMITER = ", ";

    QueryService queryService = ServiceLocator.locate(QueryService.class);

    QueryResponse queryResponse = queryService.query(
      "select global_code_sku__cr.name__v" +
        "  from pmf_global_code__c where pmf__c = '"+pmfRecordId+"'"
    );

    if (queryResponse.getResultCount() > 0) {
      return Util.stringifyFieldValues(queryResponse, "global_code_sku__cr.name__v", MAX_FIELD_LENGTH, DELIMITER);
    } else {
      return null;
    }
  }

  /*
   * Get a comma-delimited list of Impacted Country names.
   */
  private static String getImpactedCountriesDisplay(String pmfRecordId) {

    final int MAX_FIELD_LENGTH = 2000;
    final String DELIMITER = ", ";

    QueryService queryService = ServiceLocator.locate(QueryService.class);

    QueryResponse queryResponse = queryService.query(
      "select country__cr.name__v from pmf_impacted_country__c where pmf__c = '"+pmfRecordId+"'"
    );

    if (queryResponse.getResultCount() > 0) {
      return Util.stringifyFieldValues(queryResponse, "country__cr.name__v", MAX_FIELD_LENGTH, DELIMITER);
    } else {
      return null;
    }
  }

  /**
     * Update the Owner role (owner__v) on a PMF record, replacing the current user(s) with a correct user.
     * There must never be more than one user in the Owner role, and the Owner role must be populated
     * with the User identified by the "PMF Requestor" (pmf_requestor__c) field.  This method guarantees these rules.
     * Detection and removal of Groups is included just in case someone manually tries to add a Group to the Role.
     * @param pmfRecord - Record, fully populated from a PMF object record.
     */
    public static void updateOwnerRole(Record pmfRecord) {

      RecordRoleService recordRoleService = ServiceLocator.locate(RecordRoleService.class);

      String pmfRequestor = pmfRecord.getValue("pmf_requestor__c", ValueType.STRING);

      if (pmfRequestor == null) {
        // this should never happen (see the BEFORE trigger), but just in case...
        throw new RollbackException(ErrorType.UPDATE_DENIED, "PMF Requestor is required.");
      }

      RecordRole ownerRole = recordRoleService
        .getRecordRoles(VaultCollections.asList(pmfRecord), "owner__v")
        .getRecordRole(pmfRecord);

      List<String> currentUserIds = ownerRole.getUsers();
      List<Group> currentGroups = ownerRole.getGroups();

      if (currentUserIds.size() == 1 && currentUserIds.contains(pmfRequestor) &&
          currentGroups.size() == 0) {
        return;  // nothing to do
      }

      if (currentUserIds.size() > 1) {
        // we're going to remove this list of users, so exclude the Requestor if s/he's in there
        currentUserIds.remove(pmfRequestor);
      }

      RecordRoleUpdate roleUpdate = recordRoleService.newRecordRoleUpdate("owner__v", pmfRecord);

      roleUpdate.removeUsers(currentUserIds);
      roleUpdate.removeGroups(currentGroups);
      roleUpdate.addUsers(VaultCollections.asList(pmfRequestor));

      recordRoleService.batchUpdateRecordRoles(VaultCollections.asList(roleUpdate))
        .rollbackOnErrors()
        .execute();

    }

    /**
     * Populate the Regulatory role on the PMF record with the appropriate Regulatory users based
     * on the Country, PMF Type, Business Unit and Brand.
     * @param pmfRecord - Record. Fully populated from a PMF object record.
     * @param pmfUsers
     */
    public static void updateRegulatoryRole(Record pmfRecord, PmfUsers pmfUsers) {

      String countryId = pmfRecord.getValue("requestor_country__c", ValueType.STRING);
      String businessUnit = pmfRecord.getValue("business_unit__c", ValueType.PICKLIST_VALUES).get(0);
      String pmfBrand = pmfRecord.getValue("brand__c", ValueType.STRING);
      String pmfType = getObjectTypeName(pmfRecord);

      List<String> userIds = pmfUsers.getRegulatoryUsers(countryId, businessUnit, pmfBrand, pmfType);

      clearOutRole(pmfRecord, "regulatory__c");

      if (userIds.size() > 0) {
        populateRole(pmfRecord, "regulatory__c", userIds);
      } else {
        //TODO: put a notification that no Regulatory Users were found?????
      }

    } // end updateRegulatoryRole()

// CURRENTLY NOT USED...
//  /**
//   * Populate the non-Regulatory reviewer role (PMF Reviewer) on the PMF record with the appropriate  users based
//   * on the Country, Business Unit and Brand.
//   * @param pmfRecord
//   * @param pmfUsers
//   */
//  public static void updateNonRegulatoryReviewerRole(Record pmfRecord, PmfUsers pmfUsers) {
//
//    String countryId = pmfRecord.getValue("requestor_country__c", ValueType.STRING);
//    String businessUnit = pmfRecord.getValue("business_unit__c", ValueType.PICKLIST_VALUES).get(0);
//    String pmfBrand = pmfRecord.getValue("brand__c", ValueType.STRING);
//
//    List<String> userIds = pmfUsers.getNonRegulatoryReviewers(countryId, businessUnit, pmfBrand);
//
//    clearOutRole(pmfRecord, "pmf_reviewer__c");
//
//    if (userIds.size() > 0) {
//      populateRole(pmfRecord, "pmf_reviewer__c", userIds);
//    } else {
//      //TODO: put a notification that no Regulatory Users were found?????
//    }
//
//  } // end updateRegulatoryRole()

  /**
     * Populate the Plant Reviewer role on the PMF record with the appropriate Users based
     * on the PMF's Logistic Site.
     * @param pmfRecord
     * @param pmfUsers
     */
    public static void updatePlantReviewerRole(Record pmfRecord, PmfUsers pmfUsers) {

      String logisticSite = pmfRecord.getValue("logistic_site__c", ValueType.STRING);

      List<String> userIds = pmfUsers.getPlantReviewers(logisticSite);

      clearOutRole(pmfRecord, "pmf_plant_reviewer__c");

      if (userIds.size() > 0) {
        populateRole(pmfRecord, "pmf_plant_reviewer__c", userIds);
      } else {
        //TODO: put a notification that no Plant Users were found?????
      }

    }

    /**
     * Popluate the PMF record's "Packaging Number(s)" and "Artwork Document Number(s)" fields with the corresponding
     * data from each of the impacted Artwork Document record(s).
     *
     * Assumes:
     *   - the record passed as an argument has the record ID
     *   - the PMF record type is "Artwork Modification".
     * @param pmfRecordId - String - ID of PMF record
     */
    public static void setArtworkPackagingNumbers(String pmfRecordId) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);
      RecordService recordService = ServiceLocator.locate(RecordService.class);

      QueryResponse queryResponse = queryService.query(
        "select document_number__c, packaging_number__c" +
        "  from pmf_artwork_document__c" +
        " where pmf__c = '"+pmfRecordId+"'"
      );

      Iterator<QueryResult> iter = queryResponse.streamResults().iterator();

      List<String> packagingNumberList = VaultCollections.newList();
      List<String> documentNumberList = VaultCollections.newList();

      while (iter.hasNext()) {
        QueryResult queryResult = iter.next();
        String packagingNumber = queryResult.getValue("packaging_number__c", ValueType.STRING);
        String documentNumber = queryResult.getValue("document_number__c", ValueType.STRING);
        if (packagingNumber != null) {
          packagingNumberList.add(packagingNumber);
        }
        documentNumberList.add(documentNumber);
      }

      String packagingNumbers = Util.stringifyList(packagingNumberList);
      String documentNumbers = Util.stringifyList(documentNumberList);

      // There should never be more than a handful of Artwork Documents attached
      // to a PMF, so this logic is therefore unnecessary.  But including it
      // just in case.
      if (packagingNumbers.length() > 1500) {
        packagingNumbers = packagingNumbers.substring(0, 1500);
      }
      if (documentNumbers.length() > 1500) {
        documentNumbers = documentNumbers.substring(0, 1500);
      }

      Record pmfRecord = recordService.newRecordWithId("pmf__c", pmfRecordId);

      pmfRecord.setValue("packaging_numbers__c", packagingNumbers);
      pmfRecord.setValue("artwork_document_numbers__c", documentNumbers);

      recordService.batchSaveRecords(VaultCollections.asList(pmfRecord))
        .onErrors(batchOperationErrors -> {
          batchOperationErrors.stream().findFirst().ifPresent(error -> {
            String errMsg = error.getError().getMessage();
            throw new RollbackException(ErrorType.OPERATION_FAILED, errMsg);
          });
        })
        .execute();

    }

  /**
   * return true if the PMF is a Safety Variation, otherwise return false
   * @param pmfId
   * @return boolean
   */
    public static boolean isSafetyVariation(String pmfId) {
      QueryService queryService = ServiceLocator.locate(QueryService.class);
      Boolean answer = queryService.query(
        "select safety_variation__c from pmf__c where id = '"+pmfId+"'"
      )
        .streamResults()
        .findFirst()
        .get()
        .getValue("safety_variation__c", ValueType.BOOLEAN);

      if (answer == null) {
        answer = false;
      }
      return answer;
    }

// CURRENTLY NOT USED...
//  /**
//   * Return the User ID of the PMF Requestor.
//   * @param pmfId - String
//   * @return user Id - String
//   */
//    public static String getPmfRequestor(String pmfId) {
//      QueryService qs = ServiceLocator.locate(QueryService.class);
//      QueryResponse qr = qs.query("select created_by__v from pmf__c where id = '"+pmfId+"'");
//      return qr.streamResults().iterator().next().getValue("created_by__v", ValueType.STRING);
//    }

    // Add the users in the provided list to the specified Role. Roles are:
    //   - regulatory__c
    //   - pmf_plant_reviewer__c
    private static void populateRole(Record pmfRecord, String roleName, List<String> userIds) {

      RecordRoleService recordRoleService = ServiceLocator.locate(RecordRoleService.class);
      RecordRoleUpdate regulatoryRole = recordRoleService.newRecordRoleUpdate(roleName, pmfRecord);
      regulatoryRole.addUsers(userIds);
      recordRoleService.batchUpdateRecordRoles(VaultCollections.asList(regulatoryRole))
        .rollbackOnErrors()
        .execute();
    }

    // Remove all users currently in the specified Role. Roles are:
    //   - regulatory__c
    //   - pmf_plant_reviewer__c
    // Include any groups, just in case someone added a group manually.
    private static void clearOutRole(Record pmfRecord, String roleName) {

      RecordRoleService recordRoleService = ServiceLocator.locate(RecordRoleService.class);

      GetRecordRolesResponse recordRolesResponse = recordRoleService.getRecordRoles(
        VaultCollections.asList(pmfRecord), roleName
      );

      RecordRole recordRole = recordRolesResponse.getRecordRole(pmfRecord);
      List<String> userIds = recordRole.getUsers();
      List<Group> groups = recordRole.getGroups();  //include groups just in case

      if (userIds.size() > 0 || groups.size() > 0) {
        RecordRoleUpdate recordRoleUpdate = recordRoleService.newRecordRoleUpdate(roleName, pmfRecord);
        recordRoleUpdate.removeUsers(userIds);
        recordRoleUpdate.removeGroups(groups);
        recordRoleService.batchUpdateRecordRoles(VaultCollections.asList(recordRoleUpdate))
          .rollbackOnErrors()
          .execute();
      }
    }

}
