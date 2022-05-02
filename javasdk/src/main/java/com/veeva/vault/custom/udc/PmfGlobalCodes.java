package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * This class is used specifically to store the PMF Global Code record ID's and the Logistic Site record ID
 * for a PMF, to allow updating the Logistic Site field and to update the PMF's list of PMF Impacted Country
 * object records.
 *
 * An instance of this class is created in triggers.pmf_global_code.GatherGlobalCodes.java (BEFORE INSERT/UPDATE)
 *
 * That instance is then retrieved and used by triggers.pmf_global_code.UpdatePMF.java (AFTER INSERT/UPDATE)
 */

@UserDefinedClassInfo(name = "pmf_globalcodes__c")
public class PmfGlobalCodes implements RequestContextValue {

    public static final String ContextName = "pmfGlobalCodes";

    private String pmfRecordId;
    private List<String> globalCodeRecordIds; // derived from the current PMF Global Code object records
    private String logisticSiteRecordId;

    public PmfGlobalCodes() {
      //parameter-less constructor required by the system
    }

  /**
   * @param pmfRecordId - String.  The record id for the associated PMF record.
   * @param globalCodeRecordIds - List<String>.  The list of "Global Code (SKU)" object record ID's, from
   *                            the list of "PMF Global Code" object records associated with the PMF.
   * @param logisticSiteRecordId - String.
   */
    public PmfGlobalCodes(String pmfRecordId, List<String> globalCodeRecordIds, String logisticSiteRecordId) {
      this.pmfRecordId = pmfRecordId;
      this.globalCodeRecordIds = globalCodeRecordIds;
      this.logisticSiteRecordId = logisticSiteRecordId;
    }

  /**
   * @param pmfRecordId - String.  The record id for the associated PMF record.
   * @param globalCodeRecordIds - List<String>.  The list of ID's "Global Code (SKU)" object records, from
   *                            the list of "PMF Global Code" object records associated with the PMF.
   */
    public PmfGlobalCodes(String pmfRecordId, List<String> globalCodeRecordIds) {
      this.pmfRecordId = pmfRecordId;
      this.globalCodeRecordIds = globalCodeRecordIds;
      this.logisticSiteRecordId = null;
    }

    /**
     * Update the PMF's Logistic Site field.
     */
    public void updatePmfLogisticSite() {
      PMF.updateLogisticSite(pmfRecordId, logisticSiteRecordId);
    }

    /**
     * Update the PMF's Global Codes and Impacted Countries fields.
     *
     *   NOTE: this method MUST be called AFTER updatedImpactedCountries() !!
     */
    public void updatePmfGlobalCodesAndCountriesDisplay() {
      PMF.updateGlobalCodesAndCountriesDisplay(this.pmfRecordId);
    }

    /**
     * Update the PMF's list of Impacted Countries (PMF Impacted Country (pmf_impacted_country__c)).
     */
    public void updateImpactedCountries() {

      RecordService recordService = ServiceLocator.locate(RecordService.class);

      Set<String> allPossibleCountries = getAllPossibleCountries();

      // Key: Country record id; Value: PMF Impacted Country record id
      Map<String, String> currentPmfCountries = getCurrentPMFCountries();

      List<Record> records;
      List<String> countryIds;

      /////////////////////////////////////////////
      // Remove countries no longer applicable...
      /////////////////////////////////////////////
      countryIds = Util.difference(currentPmfCountries.keySet(), allPossibleCountries);
      if (countryIds.size() > 0) {
        records = VaultCollections.newList();
        for (String countryId : countryIds) {
          String pmfImpactedCountryRecordId = currentPmfCountries.get(countryId);
          records.add(recordService.newRecordWithId("pmf_impacted_country__c", pmfImpactedCountryRecordId));
        }
        recordService.batchDeleteRecords(records)
          .onErrors(batchOperationErrors -> {
            batchOperationErrors.stream().findFirst().ifPresent(error -> {
              String errMsg = error.getError().getMessage();
              throw new RollbackException(ErrorType.OPERATION_FAILED,
                "Failed to delete PMF Impacted Country record due to: " + errMsg);
            });
          })
          .execute();
      }

      //////////////////////////////////////////////////////////////
      // Add new countries to the list of PMF Impacted Countries...
      //////////////////////////////////////////////////////////////
      countryIds = Util.difference(allPossibleCountries, currentPmfCountries.keySet());
      if (countryIds.size() > 0) {
        records = VaultCollections.newList();
        for (String countryId : countryIds) {
          Record record = recordService.newRecord("pmf_impacted_country__c");
          record.setValue("pmf__c", this.pmfRecordId);
          record.setValue("country__c", countryId);
          records.add(record);
        }
        recordService.batchSaveRecords(records)
          .onErrors(batchOperationErrors -> {
            batchOperationErrors.stream().findFirst().ifPresent(error -> {
              String errMsg = error.getError().getMessage();
              throw new RollbackException(ErrorType.OPERATION_FAILED,
                "Failed to save PMF Impacted Country record due to: " + errMsg);
            });
          })
          .execute();
      }
    }

    // Return the set of all possible countries based on the list of Global Code (SKU) Record Ids
    private Set<String> getAllPossibleCountries() {

      Set<String> result = VaultCollections.newSet();

      if (globalCodeRecordIds.size() > 0) {

        StringBuilder queryBuilder = new StringBuilder(
          "select country__c from global_code_sku_country__c where global_code_sku__c contains ("
        );

        Iterator<String> iter = this.globalCodeRecordIds.iterator();
        while (iter.hasNext()) {
          queryBuilder.append("'").append(iter.next()).append("'");
          if (iter.hasNext()) {
            queryBuilder.append(",");
          }
        }
        queryBuilder
          .append(")")
          .append(" and (mdm_status__c = 'Active' or mdm_status__c = 'Project')");

        String query = queryBuilder.toString();

        QueryService queryService = ServiceLocator.locate(QueryService.class);
        QueryResponse queryResponse = queryService.query(query);

        Iterator<QueryResult> qrIter = queryResponse.streamResults().iterator();

        while (qrIter.hasNext()) {
          QueryResult qr = qrIter.next();
          result.add(qr.getValue("country__c", ValueType.STRING));
        }

      }  // end if (globalCodeRecordIds.size() > 0)

      return result;
    }

    // Return a map of all PMF Impacted Country (pmf_impacted_country__c) records for the PMF.
    // Country record ID is the Key; PMF Impacted Country record ID is the Value.
    Map<String, String> getCurrentPMFCountries() {

      Map<String, String> result = VaultCollections.newMap();

      String query = "select id, country__c from pmf_impacted_country__c where pmf__c = '"+pmfRecordId+"'";

      QueryService queryService = ServiceLocator.locate(QueryService.class);
      QueryResponse queryResponse = queryService.query(query);

      Iterator<QueryResult> iter = queryResponse.streamResults().iterator();

      while (iter.hasNext()) {
        QueryResult qr = iter.next();
        result.put(
          qr.getValue("country__c", ValueType.STRING),
          qr.getValue("id", ValueType.STRING)
        );
      }

      return result;
    }

 }
