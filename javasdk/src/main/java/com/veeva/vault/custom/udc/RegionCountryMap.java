package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Helps to map regions and countries.
 *
 *   Find whether a Country is in a Region. 
 */

@UserDefinedClassInfo
public class RegionCountryMap {

    private Map<String, List<String>> regionMap;
    
    public RegionCountryMap() {

      this.regionMap = VaultCollections.newMap();

      Iterator<QueryExecutionResult> iter = QueryUtil.query(
        "select region__c, id from country__v"
      ).streamResults().iterator();

      while (iter.hasNext()) {
        QueryExecutionResult result = iter.next();
        String regionId = result.getValue("region__c", ValueType.STRING);
        String countryId = result.getValue("id", ValueType.STRING);
        List<String> countries = regionMap.get(regionId);
        if (countries == null) {
          countries = VaultCollections.newList();
          regionMap.put(regionId, countries);
        }
        countries.add(countryId);
      }
    }

    public boolean isCountryInRegion(String regionId, String countryId) {
      List<String> countries = this.regionMap.get(regionId);
      if (countries == null) {
          throw new RollbackException(ErrorType.OPERATION_FAILED, "'"+regionId+"' is not a region");
      }
      return countries.contains(countryId);
    }
}