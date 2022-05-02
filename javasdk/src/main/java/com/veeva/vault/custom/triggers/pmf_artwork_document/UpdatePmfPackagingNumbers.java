package com.veeva.vault.custom.triggers.pmf_artwork_document;

import com.veeva.vault.custom.udc.PMF;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;

/**
 *
 * This trigger updates the PMF's "Artwork Document Number(s)" and "Packaging Numbers(s)" field.
 *
 */

@RecordTriggerInfo(object = "pmf_artwork_document__c", events = {RecordEvent.AFTER_INSERT, RecordEvent.AFTER_DELETE},
  order = TriggerOrder.NUMBER_2
)
public class UpdatePmfPackagingNumbers implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      if (recordTriggerContext.getRecordChanges().size() > 1) {
        return;  // support single-record operations only
      }

      Record record = null;

      if (recordTriggerContext.getRecordEvent() == RecordEvent.AFTER_DELETE) {
        record = recordTriggerContext.getRecordChanges().get(0).getOld();
      } else {
        record = recordTriggerContext.getRecordChanges().get(0).getNew();
      }

      String pmfRecordId = record.getValue("pmf__c", ValueType.STRING);

      PMF.setArtworkPackagingNumbers(pmfRecordId);
    }
}

