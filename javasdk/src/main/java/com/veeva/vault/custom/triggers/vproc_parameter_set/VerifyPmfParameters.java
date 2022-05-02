package com.veeva.vault.custom.triggers.vproc_parameter_set;

import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.custom.udc.VaultField;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.json.JsonService;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.List;

/**
 *
 * This validates the Parameters and Artwork Fields values.
 *
 */

@RecordTriggerInfo(object = "vproc_parameter_set__c", events = {RecordEvent.BEFORE_UPDATE}, order = TriggerOrder.NUMBER_1)
public class VerifyPmfParameters implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {

        Record newRecord = inputRecord.getNew();

        String moduleName = newRecord.getValue("name__v", ValueType.STRING);

        if (moduleName.equals("pmf")) {
          verifyParameters(newRecord);
          verifyArtworkFields(newRecord);
        }

      }
    	
    }

    private void verifyParameters(Record record) {
      JsonService jsonService = ServiceLocator.locate(JsonService.class);
      String parameters = record.getValue("parameters__c", ValueType.STRING);
      jsonService.readJson(parameters).getJsonObject();  // This will blow up if the JSON is invalid
    }

    private void verifyArtworkFields(Record record) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      String artworkFieldsString = record.getValue("artwork_fields__c", ValueType.STRING);

      // This will blow up if the string is invalid...
      List<VaultField> artworkFields = Util.parseVaultFieldInfo(artworkFieldsString);

      List <String> fieldNames = VaultCollections.newList();
      for (VaultField field : artworkFields) {
        fieldNames.add(field.fieldName);
      }

      // This will blow up if any field names are wrong...
      queryService.query(
        "select "+Util.stringifyList(fieldNames)+" from documents where type__v = 'artwork__c' and id = 1"
      );

    }

}

