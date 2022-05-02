package com.veeva.vault.custom.triggers.pmf_global_code;

import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.TriggerOrder;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;

/**
 *
 * This trigger acts on a single inserted record and copies the Implementation Date and Implementation Date Meaning
 * fields from the previously created record if both of those fields on the new record are blank.
 *
 */

@RecordTriggerInfo(object = "pmf_global_code__c", events = {RecordEvent.BEFORE_INSERT}, order = TriggerOrder.NUMBER_3)
public class CopyImplementationDate implements RecordTrigger {

    private static final String REQUESTED_IMPLEMENTATION_DATE = "requested_implementation_date__c";
    private static final String IMPLEMENTATION_DATE_MEANING = "implementation_date_meaning__c";

    public void execute(RecordTriggerContext recordTriggerContext) {

      if (recordTriggerContext.getRecordChanges().size() > 1) {
        return;  // this trigger acts on single-record inserts only
      }

      Record record = recordTriggerContext.getRecordChanges().get(0).getNew();

      if (
        record.getValue(REQUESTED_IMPLEMENTATION_DATE, ValueType.DATE) == null &&
        record.getValue(IMPLEMENTATION_DATE_MEANING, ValueType.PICKLIST_VALUES) == null
      ) {

        String pmfId = record.getValue("pmf__c", ValueType.STRING);

        QueryService queryService = ServiceLocator.locate(QueryService.class);
        QueryResponse queryResponse = queryService.query(
          "select requested_implementation_date__c, implementation_date_meaning__c" +
          "  from pmf_global_code__c" +
          " where pmf__c = '"+pmfId+"'" +
          " order by created_date__v desc pagesize 1"
        );

        Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();

        if (iterator.hasNext()) {
          QueryResult queryResult = iterator.next();
          Object date = queryResult.getValue(REQUESTED_IMPLEMENTATION_DATE, ValueType.DATE);
          Object meaning = queryResult.getValue(IMPLEMENTATION_DATE_MEANING, ValueType.PICKLIST_VALUES);
          record.setValue(REQUESTED_IMPLEMENTATION_DATE, date);
          record.setValue(IMPLEMENTATION_DATE_MEANING, meaning);
        }

      }

    }
}
