package com.veeva.vault.custom.triggers.pmf_global_code;

import com.veeva.vault.custom.udc.PmfGlobalCodes;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.data.*;

/**
 * Update the Logistic Site on the PMF, if applicable.
 * Update the PMF's list of Impacted Countries.
 */

@RecordTriggerInfo(object = "pmf_global_code__c", events = {RecordEvent.AFTER_INSERT, RecordEvent.AFTER_UPDATE})
public class UpdatePMF implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

      /*
        The PmfGlobalCodes instance is created in the BEFORE INSERT/UPDATE trigger "GatherGlobalCodes".  It is,
        however, only created if the Global Code field has changed, which is why the nullness test is included.
       */

      PmfGlobalCodes pmfGlobalCodes = RequestContext.get().getValue(PmfGlobalCodes.ContextName, PmfGlobalCodes.class);
      if (pmfGlobalCodes != null) {
        pmfGlobalCodes.updatePmfLogisticSite();
        pmfGlobalCodes.updateImpactedCountries();
        pmfGlobalCodes.updatePmfGlobalCodesAndCountriesDisplay();
      }

    }
}

