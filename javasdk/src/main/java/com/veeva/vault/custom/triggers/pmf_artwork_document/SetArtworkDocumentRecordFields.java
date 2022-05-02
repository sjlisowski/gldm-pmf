package com.veeva.vault.custom.triggers.pmf_artwork_document;

import com.veeva.vault.custom.udc.ErrorType;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.data.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.util.Iterator;

/**
 *
 * This trigger populates fields on the PMF Artwork Document object from field values
 * on the selected Artwork document.
 *
 * This trigger also performs some validation on the selected artwork document version
 * before populating the fields.
 *
 * Artwork Document fields:
 *   - Document Number (document_number__v)
 *   - Name (name__v)
 *   - Brand (product__v)
 *   - Packaging Type (packaging_type1__c) - Object (Packaging Type)
 *   - Packaging Number (packaging_number__c) - Text (200)
 */

@RecordTriggerInfo(object = "pmf_artwork_document__c", events = {RecordEvent.BEFORE_INSERT})
public class SetArtworkDocumentRecordFields implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

        if (recordTriggerContext.getRecordChanges().size() > 1) {
            return; // Records in this object are never expected to be loaded in bulk
        }

        Record newRecord = recordTriggerContext.getRecordChanges().get(0).getNew();

        verifyDocumentType(newRecord);
        verifyNotDuplicate(newRecord);
        populateDocumentFields(newRecord);
    }

    private void verifyDocumentType(Record newRecord) {

        QueryService queryService = ServiceLocator.locate(QueryService.class);
        String artDocVerId = newRecord.getValue("artwork_document__c", ValueType.STRING);

        // this query is designed to return a single result if the document identified by
        // the version_id is truly an Artwork document.  As of 2022-02-27, no longer
        // checking status is steadystate.  Allowed statuses are controlled by reference
        // constraint on the document field on the PMF Artwork Document object
        String query =
          "select id from documents " +
            " where version_id = '"+ artDocVerId +"' " +
            "   and toName(type__v) = 'artwork__c'";

        long recordCount = queryService.query(query).getResultCount();

        if (recordCount == 0) {
            throw new RollbackException(ErrorType.INVALID_DOCUMENT,
                "Please select an Artwork document in status \"Approved for Use\"."
            );
        }
    }

    // Verify that this Artwork Document version is not used in a different PMF
    private void verifyNotDuplicate(Record newRecord) {

        QueryService queryService = ServiceLocator.locate(QueryService.class);
        String artDocVerId = newRecord.getValue("artwork_document__c", ValueType.STRING);

        String query =
            "select id, (select id, name__v from pmf__cr) from pmf_artwork_document__c " +
            " where artwork_document__c = '"+ artDocVerId +"'";

        QueryResponse queryResponse = queryService.query(query);
        long recordCount = queryResponse.getResultCount();

        if (recordCount > 0) {
          QueryResult subQueryResult = queryResponse
              .streamResults()
              .findFirst()
              .get()
              .getSubqueryResponse("pmf__cr")
              .streamResults()
              .findFirst()
              .get();
          String foundPmfName = subQueryResult.getValue("name__v", ValueType.STRING);
          throw new RollbackException(ErrorType.DUPLICATE_DOCUMENT,
            "The Artwork document version you selected is already attached to " + foundPmfName + "."
          );
        }
    }

    private void populateDocumentFields(Record newRecord) {

        QueryService queryService = ServiceLocator.locate(QueryService.class);
        String artDocVerId = newRecord.getValue("artwork_document__c", ValueType.STRING);

        String query = "select " +
            "document_number__v, " +
            "packaging_number__c, " +
            "(select name__v from document_product__vr), " +
            "(select name__v from document_packaging_type1__cr)" +
            "from documents " +
            "where version_id = '" + artDocVerId + "'";

        Iterator<QueryResult> iterator = queryService.query(query).streamResults().iterator();
        QueryResult qr = (QueryResult) iterator.next();

        String docNbr = qr.getValue("document_number__v", ValueType.STRING);
        String packagingNbr = qr.getValue("packaging_number__c", ValueType.STRING);
        String brand = Util.stringifyFieldValues(
            qr.getSubqueryResponse("document_product__vr"), "name__v", 40, ", "
        );
        String packagingType = Util.stringifyFieldValues(
            qr.getSubqueryResponse("document_packaging_type1__cr"), "name__v", 200, ", "
        );

        newRecord.setValue("document_number__c", docNbr);
        newRecord.setValue("packaging_number__c", packagingNbr);
        newRecord.setValue("brand__c", brand);
        newRecord.setValue("packaging_type__c", packagingType);
    }
}