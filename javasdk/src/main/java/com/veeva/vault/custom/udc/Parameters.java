package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.json.JsonData;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonService;
import com.veeva.vault.sdk.api.json.JsonValueType;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

/**
 * This class is an interface for the "VPROC Parameter Set" object record.
 *
 * Static methods in this class:
 *
 *   getVaultDomain - Return the Domain part of the vault's URL.
 */

@UserDefinedClassInfo
public class Parameters {

    /**
     * Return the Domain part of the vault's URL.
     * @return - String.
     */
    public static String getVaultDomain() {
      //return getPmfParameters().getValue("vaultDomain", JsonValueType.STRING);
      VaultInformationService vaultInformationService = ServiceLocator.locate(VaultInformationService.class);
      VaultInformation vaultInformation = vaultInformationService.getLocalVaultInformation();
      return vaultInformation.getDns();
    }

    private static JsonObject getPmfParameters() {
        JsonService jsonService = ServiceLocator.locate(JsonService.class);
        QueryService queryService = ServiceLocator.locate(QueryService.class);
        QueryResponse queryResponse = queryService.query(
          "select parameters__c from vproc_parameter_set__c where name__v = 'pmf'"
        );
        QueryResult queryResult = queryResponse.streamResults().iterator().next();
        JsonData jsonData = jsonService.readJson(queryResult.getValue("parameters__c", ValueType.STRING));
        return jsonData.getJsonObject();
    }
}