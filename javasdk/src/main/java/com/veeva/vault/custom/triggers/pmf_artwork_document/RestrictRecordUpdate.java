package com.veeva.vault.custom.triggers.pmf_artwork_document;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.data.RecordEvent;
import com.veeva.vault.sdk.api.data.RecordTrigger;
import com.veeva.vault.sdk.api.data.RecordTriggerContext;
import com.veeva.vault.sdk.api.data.RecordTriggerInfo;

/**
 *
 * This trigger prohibits updates to "PMF Artwork Document" object records.
 *
 * This obviates the need to create an Object Lifecycle simply for the purpose of restricting updates
 * with Atomic Security.
 */

@RecordTriggerInfo(object = "pmf_artwork_document__c", events = {RecordEvent.BEFORE_UPDATE})
public class RestrictRecordUpdate implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      throw new RollbackException(ErrorType.UPDATE_DENIED,
          "Updates to the Artwork Document selection are not allowed.  " +
          "To change the Artwork, delete this record and create a new one."
      );

    }
}