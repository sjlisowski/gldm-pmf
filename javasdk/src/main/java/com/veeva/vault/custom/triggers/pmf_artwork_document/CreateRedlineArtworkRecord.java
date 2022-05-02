package com.veeva.vault.custom.triggers.pmf_artwork_document;

import com.veeva.vault.custom.udc.RedlineArtwork;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;

/**
 *
 * This trigger creates a new document of type "PMF Redline Artwork" based on the Artword document
 * selected in this trigger's record, then creates a new "PMF Redline Artwork Document"
 * (pmf_redline_artwork_document__c) object record referencing the newly created document.
 *
 */

@RecordTriggerInfo(object = "pmf_artwork_document__c", events = {RecordEvent.AFTER_INSERT},
  order = TriggerOrder.NUMBER_1
)
public class CreateRedlineArtworkRecord implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      if (recordTriggerContext.getRecordChanges().size() > 1) {
        return;  // support single-record operations only
      }

      for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {

        Record newRecord = inputRecord.getNew();

        String pmfId = newRecord.getValue("pmf__c", ValueType.STRING);
        String artDocVersionId = newRecord.getValue("artwork_document__c", ValueType.STRING);
        String id = newRecord.getValue("id", ValueType.STRING);
        String documentNumber = newRecord.getValue("document_number__c", ValueType.STRING);
        String brand = newRecord.getValue("brand__c", ValueType.STRING);
        String packagingType = newRecord.getValue("packaging_type__c", ValueType.STRING);
        String packagingNumber = newRecord.getValue("packaging_number__c", ValueType.STRING);

        RedlineArtwork.createAndAttach(
          pmfId, artDocVersionId, id, documentNumber, brand, packagingType, packagingNumber
        );

      }

    }
}

