package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.List;

/**
 * This class manages access to the PMF User Role Setup (pmf_user_role_setup__c) object, and
 * contains the logic to identify the users who should occupy the Regulatory and Plant Reviewer
 * roles for a PMF.
 */

@UserDefinedClassInfo
public class PmfUsers {

    private static final String CONSUMER_BU = "cx__c";
    private static final String PHARMACEUTICAL_BU = "rx__c";

    private List<QueryResult> usersQueryResults;  // all records from "PMF User Role Setup" (pmf_user_role_setup__c)

    private Countries countries = null; // for assigning region-based regulatory reviewers

    public PmfUsers() {
      this.usersQueryResults = getPMFUserRecords();
    }

    /**
     * Return a list of user ids for all users stored in object pmf_user__c.
     * @return List<String> List of user IDs for all users in the pmf_user__c object
     */
    public static List<String> getUserIdsAll() {

        QueryService queryService = ServiceLocator.locate(QueryService.class);
        List<String> userIds = VaultCollections.newList();
        String query = "select user__c from pmf_user_role_setup__c";

        Iterator<QueryResult> iterator = queryService.query(query).streamResults().iterator();

        while (iterator.hasNext()) {
            QueryResult qr = (QueryResult) iterator.next();
            String userId = qr.getValue("user__c", ValueType.STRING);
            userIds.add(userId);
        }

        return userIds;
    }

  /**
   * Returns a QueryResponse of a query of all PMF User records including all fields.
   * This QueryResponse can be used in subsequent calls to methods in this Class.
   * @return QueryResponse
   */
//  private static QueryResponse getPMFUserRecords() {
    private static List<QueryResult> getPMFUserRecords() {

      List<QueryResult> queryResults = VaultCollections.newList();

      QueryService queryService = ServiceLocator.locate(QueryService.class);
      QueryResponse qResponse = queryService.query(
        "select user__c, role__c, business_unit__c, brand__c, country__c, region__c, logistic_site__c, pmf_type__c" +
          " from pmf_user_role_setup__c"
      );

      Iterator<QueryResult> iter = qResponse.streamResults().iterator();

      while (iter.hasNext()) {
        queryResults.add(iter.next());
      }

      return queryResults;
  }

  /**
   * Return a Set of Regulatory User ID's based on the provided parameters.
   * @param pmfCountry - String. the ID of the PMF's country
   * @param pmfBU - String. the PMF's business unit
   * @param pmfBrand - String.  The ID of the PMF's Brand
   * @param pmfType - String. The api name of the type of PMF
   * @return List<String> - list of regulatory user id's based on the provided parameters
   * occupants for the given PMF record.
   */
  public List<String> getRegulatoryUsers(String pmfCountry, String pmfBU, String pmfBrand, String pmfType) {

      if (this.countries == null) {
        this.countries = new Countries(); // this is for resolving region-based Regulatory
      }

      List<String> userIds = VaultCollections.newList();

      String regulatoryRoleId = Util.getRoleId("regulatory__c");
      String USCountryId = Util.getUSCountryId();

      Iterator<QueryResult> iterator = this.usersQueryResults.iterator();

      while (iterator.hasNext()) {

        QueryResult user = iterator.next();
        String userId = user.getValue("user__c", ValueType.STRING);
        String userRole = user.getValue("role__c", ValueType.STRING);
        String userCountry = user.getValue("country__c", ValueType.STRING);
        String userRegion = user.getValue("region__c", ValueType.STRING);
        String userBU = Util.getSinglePicklistValue(user.getValue("business_unit__c", ValueType.PICKLIST_VALUES));
        String userBrand = user.getValue("brand__c", ValueType.STRING);
        String userPmfType = Util.getSinglePicklistValue(user.getValue("pmf_type__c", ValueType.PICKLIST_VALUES));

        if (userRole.equals(regulatoryRoleId)) {

          if (pmfCountry.equals(USCountryId)) {
            if (userCountry != null && userCountry.equals(USCountryId)) {
              if (
                  (userBU == null || userBU.equals(pmfBU)) &&
                  (userBrand == null || userBrand.equals(pmfBrand))
              ) {
                  userIds.add(userId);
              }
            }
          } else {
            // PMF country is not US...
            if (userCountry == null || !userCountry.equals(USCountryId)) {
              if (
                  (userBU == null || userBU.equals(pmfBU)) &&
                  (userBrand == null || userBrand.equals(pmfBrand)) &&
                  (userRegion == null || userRegion.equals(countries.getRegion(pmfCountry))) &&
                  (userCountry == null || userCountry.equals(pmfCountry))
              ) {
                userIds.add(userId);
              }
            }
          }

        }

      } // end while

      return userIds;
    }

    // This is currently not used, as it was decided that non-regulatory reviewers will be
    // selected by the PMF Requestor.
  /**
   * Return a Set of User ID's for non-Regulatory reviewers based on the provided parameters.
   * At initial implementation, this role was applicable to US PMF's only, however, this method
   * is designed to accommodate any country.
   * @param pmfCountry - String. the ID of the PMF's country
   * @param pmfBU - String. the PMF's business unit
   * @param pmfBrand - String.  The ID of the PMF's Brand
   * @return List<String> - list of regulatory user id's based on the provided parameters
   * occupants for the given PMF record.
   */
  public List<String> getNonRegulatoryReviewers(String pmfCountry, String pmfBU, String pmfBrand) {

    List<String> userIds = VaultCollections.newList();

    String reviewerRoleId = Util.getRoleId("pmf_reviewer__c");

    Iterator<QueryResult> iterator = this.usersQueryResults.iterator();

    while (iterator.hasNext()) {

      QueryResult user = iterator.next();
      String userId = user.getValue("user__c", ValueType.STRING);
      String userRole = user.getValue("role__c", ValueType.STRING);
      String userCountry = user.getValue("country__c", ValueType.STRING);
      String userBU = Util.getSinglePicklistValue(user.getValue("business_unit__c", ValueType.PICKLIST_VALUES));
      String userBrand = user.getValue("brand__c", ValueType.STRING);

      if (userRole.equals(reviewerRoleId)) {
        if (userCountry == null || userCountry.equals(pmfCountry)) {
          if (userBU == null || userBU.equals(pmfBU)) {
            if (userBrand == null || userBrand.equals(pmfBrand)) {
              userIds.add(userId);
            }
          }
        }
      }

    } // end while

    return userIds;
  }

  /**
     * Return a Set of Plant Reviewer User ID's based on the provided parameters.
     * @param pmfLogisticSite - String. the ID of the PMF's Logistic Site
     * @return List<String> - list of regulatory user id's based on the provided parameters
     * occupants for the given PMF record.
     */
    public List<String> getPlantReviewers(String pmfLogisticSite) {

      List<String> userIds = VaultCollections.newList();

      String plantReviewRoleId = Util.getRoleId("pmf_plant_reviewer__c");

      Iterator<QueryResult> iterator = this.usersQueryResults.iterator();

      while (iterator.hasNext()) {

        QueryResult user = iterator.next();

        String userRole = user.getValue("role__c", ValueType.STRING);
        String userLogisticSite = user.getValue("logistic_site__c", ValueType.STRING);

        if (userRole.equals(plantReviewRoleId)) {
          if (pmfLogisticSite != null && userLogisticSite != null && userLogisticSite.equals(pmfLogisticSite)) {
            userIds.add(user.getValue("user__c", ValueType.STRING));
          }
        }
      }

      return userIds;
    }
}