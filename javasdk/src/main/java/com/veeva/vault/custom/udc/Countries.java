package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;
import java.util.Map;

/**
 * This class provides access to the records in the Country object.
 */

@UserDefinedClassInfo
public class Countries {

  Map<String, QueryResult> countries;
    
  public Countries() {
    this.countries = VaultCollections.newMap();
    getCountries();
  }

  public String getRegion(String countryId) {
    QueryResult queryResult = countries.get(countryId);
    return queryResult.getValue("region__c", ValueType.STRING);
  }

  private void getCountries() {
    QueryService queryService = ServiceLocator.locate(QueryService.class);
    QueryResponse queryResponse = queryService.query(
      "select id, name__v, abbreviation__c, region__c from country__v"
    );
    Iterator<QueryResult> iter = queryResponse.streamResults().iterator();
    while (iter.hasNext()) {
      QueryResult queryResult = iter.next();
      this.countries.put(queryResult.getValue("id", ValueType.STRING), queryResult);
    }
  }

}
//
//class Country {
//  public String id;
//  public String name;
//  public String abbreviation;
//  public String regionId;
//
//  public Country(String id, String name, String abbreviation, String regionId) {
//    this.id = id;
//    this.name = name;
//    this.abbreviation = abbreviation;
//    this.regionId = regionId;
//  }
//}
