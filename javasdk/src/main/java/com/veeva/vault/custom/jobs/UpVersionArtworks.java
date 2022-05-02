package com.veeva.vault.custom.jobs;

import com.veeva.vault.custom.udc.*;
import com.veeva.vault.sdk.api.connection.ConnectionContext;
import com.veeva.vault.sdk.api.connection.ConnectionService;
import com.veeva.vault.sdk.api.connection.ConnectionUser;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.document.*;
import com.veeva.vault.sdk.api.job.*;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/*
  This Job Process creates new Draft versions of the PMF's Impacted Artwork documents.

  An exception can occur when saving the new version (migrateDocumentVersion()),
  therefore the Chunk Size for this job MUST be set to 1, so that an exception on one
  Document won't terminate the Job completely.
 */

@JobInfo(adminConfigurable = true)
  public class UpVersionArtworks implements Job {

    static final String ARTWORK_STATE_APPROVED_FOR_USE = "approved_for_distribution__c";
    static final String ARTWORK_STATE_DRAFT = "draft__c";

    public JobInputSupplier init(JobInitContext jobInitContext) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);
      JobLogger logger = jobInitContext.getJobLogger();

      String pmfRecordId = jobInitContext.getJobParameter("pmfRecordId", JobParamValueType.STRING);
      logger.log("Processing PMF " + pmfRecordId);

      List<JobItem> jobItems = VaultCollections.newList();

      String query = "select id, artwork_document__c from pmf_artwork_document__c where pmf__c = '"+pmfRecordId+"'";
      logger.log("Executing VQL query: " + query);

      QueryResponse queryResponse = queryService.query(query);
      logger.log("VQL result count: " + queryResponse.getResultCount());

      Iterator<QueryResult> iter = queryResponse.streamResults().iterator();

      while (iter.hasNext()) {
        QueryResult qr = iter.next();
        String artDocVersionId = qr.getValue("artwork_document__c", ValueType.STRING);
        String artDocRecordId = qr.getValue("id", ValueType.STRING);
        JobItem jobItem = jobInitContext.newJobItem();
        jobItem.setValue("artDocVersionId", artDocVersionId);
        jobItem.setValue("artDocRecordId", artDocRecordId);
        jobItem.setValue("pmfRecordId",pmfRecordId);
        jobItems.add(jobItem);
        logger.log("Added job item for artwork version " + artDocVersionId);
      }

      return jobInitContext.newJobInput(jobItems);
    }

    public void process(JobProcessContext jobProcessContext) {

      JobLogger logger = jobProcessContext.getJobLogger();

      List<JobItem> items = jobProcessContext.getCurrentTask().getItems();

      TaskOutput taskOutput = jobProcessContext.getCurrentTask().getTaskOutput();

      for (JobItem jobItem : items) {
        String pmfRecordId = jobItem.getValue("pmfRecordId", JobValueType.STRING);
        String artDocVersionId = jobItem.getValue("artDocVersionId", JobValueType.STRING);
        String artDocRecordId = jobItem.getValue("artDocRecordId", JobValueType.STRING);
        processArtworkDocument(logger, pmfRecordId, artDocVersionId, artDocRecordId);
      }

      taskOutput.setState(TaskState.SUCCESS);
      logger.log("Task successful");
    }

    public void completeWithSuccess(JobCompletionContext jobCompletionContext) {
       JobLogger logger = jobCompletionContext.getJobLogger();
       logger.log("All tasks completed successfully");
    }

    public void completeWithError(JobCompletionContext jobCompletionContext) {
       JobResult result = jobCompletionContext.getJobResult();

       JobLogger logger = jobCompletionContext.getJobLogger();
       logger.log("completeWithError: " + result.getNumberFailedTasks() + " tasks failed out of " + result.getNumberTasks());

       List<JobTask> tasks = jobCompletionContext.getTasks();
       for (JobTask task : tasks) {
         TaskOutput taskOutput = task.getTaskOutput();
         if (TaskState.ERRORS_ENCOUNTERED.equals(taskOutput.getState())) {
            logger.log(task.getTaskId() + " failed with error message " + taskOutput.getValue("firstError", JobValueType.STRING));
         }
       }
    }

    /*
       Process the PMF's Impacted Artwork document version based on the lifecycle state of that version, and taking
       into account whether a new version has already been created...
     */
    private void processArtworkDocument(
      JobLogger logger, String pmfRecordId, String artDocVersionId, String artDocRecordId
    ) {
      QueryService queryService = ServiceLocator.locate(QueryService.class);

      logger.log("Starting processArtworkDocument() for artwork document " + artDocVersionId);

      DocVersionIdParts docVersionIdParts = new DocVersionIdParts(artDocVersionId);

      String query = "select version_id, toName(status__v), pmf__c from documents where id = " + docVersionIdParts.id;

      logger.log(artDocVersionId + ": " + query);
      QueryResult queryResult = queryService.query(query).streamResults().findFirst().get();

      String currentArtworkVersionId = queryResult.getValue("version_id", ValueType.STRING);
      String currentArtworkState = queryResult.getValue("status__v", ValueType.PICKLIST_VALUES).get(0);
      String currentArtworkPmf = Util.getFirst(queryResult.getValue("pmf__c", ValueType.REFERENCES));

      logger.log(artDocVersionId + ": current version id: " + currentArtworkVersionId );
      logger.log(artDocVersionId + ": current artwork state: " + currentArtworkState);
      logger.log(artDocVersionId + ": current artwork pmf: " + currentArtworkPmf);

      String impactedArtworkVersionState = getArtworkVersionState(artDocVersionId);
      int impactedArtworkMajorVersion = docVersionIdParts.major;
      int currentArtworkMajorVersion = DocVersionIdParts.major(currentArtworkVersionId);

      logger.log(artDocVersionId + ": original artwork state when linked to PMF: " + impactedArtworkVersionState);

      if (
          impactedArtworkVersionState.equals(ARTWORK_STATE_APPROVED_FOR_USE) &&
          currentArtworkVersionId.equals(artDocVersionId) &&
          currentArtworkState.equals(ARTWORK_STATE_APPROVED_FOR_USE)
      ) {
        upVersionArtworkDocument(logger, pmfRecordId, artDocVersionId, artDocRecordId);
      }
      else if (
          impactedArtworkVersionState.equals(ARTWORK_STATE_APPROVED_FOR_USE) &&
          !currentArtworkVersionId.equals(artDocVersionId) &&
          currentArtworkPmf == null
      ) {
        setArtworkPmf(pmfRecordId, artDocVersionId, artDocRecordId, logger);
      }
      else if (
          impactedArtworkVersionState.equals(ARTWORK_STATE_DRAFT) &&
          currentArtworkMajorVersion == impactedArtworkMajorVersion &&
          currentArtworkPmf == null
      ) {
        setArtworkPmf(pmfRecordId, artDocVersionId, artDocRecordId, logger);
      }
      else {
        logger.log(artDocVersionId + " not processed due to current state");
      }
    }

    /*
     * create the new Draft version of the approved Artwork document...
     */
    private void upVersionArtworkDocument(
      JobLogger logger, String pmfRecordId, String artDocVersionId, String artDocRecordId
    ) {

      logger.log("Starting upVersionArtworkDocument() for " + artDocVersionId);

      ConnectionService connectionService = ServiceLocator.locate(ConnectionService.class);
      DocumentService documentService = ServiceLocator.locate((DocumentService.class));
      QueryService queryService = ServiceLocator.locate(QueryService.class);

      ConnectionContext connectionContext = connectionService.newConnectionContext(
        "pmf_local_connection__c", ConnectionUser.SDK_CURRENT_USER    //.CONNECTION_AUTHORIZED_USER
      );

      List<VaultField> artDocFields = getArtworkDocumentFieldNamesAndTypes();

      List<String> fieldNames = getFieldNames(artDocFields);

      // check to see if the latest version of this document is the Steady State version
      // that is referenced by the PMF Artwork Document record.  In other words, we're
      // verifying that the document has not yet received a new Draft...
      StringBuilder sbQuery = new StringBuilder();
      sbQuery
        .append("select")
        .append(" version_id, ")
        .append(Util.stringifyList(fieldNames))
        .append(" from documents")
        .append(" where version_id = '").append(artDocVersionId).append("'")
        .append(" and status__v = steadystate()");
      String query = sbQuery.toString();

      logger.log(query);

      QueryResponse queryResponse = queryService.query(query);

      if (queryResponse.getResultCount() == 0) {
        // The Steady State version referenced by the PMF is no longer the latest version.
        // Check if the latest version has the PMF ID.  If this is the case, that means that
        // this new Draft was previously created by this PMF, and no further action is required.
        QueryResult queryResult = queryService.query(
          "select pmf__c from documents where id = " + DocVersionIdParts.id(artDocVersionId)
        ).streamResults().findFirst().get();

        String pmf = Util.getFirst(queryResult.getValue("pmf__c", ValueType.PICKLIST_VALUES));

        if (pmf == null) {
          // a new Draft exists, but is not connected to the PMF...
          setArtworkPmf(pmfRecordId, artDocVersionId, artDocRecordId, logger);
          logger.log("New version referencing this PMF was not found for " + artDocVersionId);
          sendErrorNotification(pmfRecordId, artDocVersionId, "A new Draft version may already have been created.");
        } else {
          logger.log("A new Draft version already exists of artwork document " + artDocVersionId);
        }
        return;
      }

      // The above query will only ever return 1 result, if any...
      QueryResult queryResult = queryResponse.streamResults().findFirst().get();

      DocumentSourceFileReference sourceFile = documentService.newDocumentSourceFileReference(
        connectionContext, artDocVersionId
      );

      DocVersionIdParts docVersionIdParts = new DocVersionIdParts(artDocVersionId);

      DocumentVersion newVersion = documentService.newVersion(docVersionIdParts.id);

      newVersion.setValue("major_version_number__v", BigDecimal.valueOf(docVersionIdParts.major));
      newVersion.setValue("minor_version_number__v", BigDecimal.valueOf(docVersionIdParts.minor + 1));
      newVersion.setValue("status__v", VaultCollections.asList("Draft"));
      newVersion.setValue("pmf__c", VaultCollections.asList(pmfRecordId));
      newVersion.setValue("pmf_annotated_version__c", getRedlineDocumentURL(artDocRecordId));

      boolean missingRequiredField = false;

      for (VaultField field : artDocFields) {
        Object value = queryResult.getValue(field.fieldName, field.valueType);
        if (field.required && value == null) {
          logger.log("Missing required field: " + field.fieldName);
          missingRequiredField = true;
          break;
        }
        newVersion.setValue(field.fieldName, queryResult.getValue(field.fieldName, field.valueType));
      }

      if (missingRequiredField) {
        logger.log("Artwork document " + artDocVersionId + " has missing required fields.  Cannot be up-versioned");
        sendErrorNotification(pmfRecordId, artDocVersionId, "Possible missing required fields in Approved version.");
        return;
      }

      newVersion.setSourceFile(sourceFile);

      logger.log("Creating new version for " + artDocVersionId + " with migrateDocumentVersions()");
      SaveDocumentVersionsResponse response = documentService.migrateDocumentVersions(
        VaultCollections.asList(newVersion)
      );
      if (response.getSuccesses().size() == 0) {
        logger.log("Failed to create new draft version for artwork document " + artDocVersionId + ". Reason unknown.");
        sendErrorNotification(pmfRecordId, artDocVersionId, "Unknown.");
      } else {
        String newDocVersionId = response.getSuccesses().get(0).getDocumentVersionId();
        attachNewDocVersionToPmf(pmfRecordId, artDocVersionId, newDocVersionId, logger);
        startPmfDraftWorkflow(artDocVersionId, logger);
        logger.log("Successfully created new draft version for artwork document " + artDocVersionId);
      }
    }

    /*
     * A new version of the impacted Artwork document was created in the UI.  Set the new version's PMF field
     * to the PMF record ID, and attach the new version to the PMF.
     */
    private void setArtworkPmf(
      String pmfRecordId, String artDocVersionId, String artDocRecordId, JobLogger logger
    ) {
      DocumentService documentService = ServiceLocator.locate(DocumentService.class);
      QueryService queryService = ServiceLocator.locate(QueryService.class);

      logger.log("Starting setArtworkPmf() for " + artDocVersionId);

      String artDocId = DocVersionIdParts.id(artDocVersionId);

      DocumentVersion newVersion = documentService.newVersion(artDocId);
      newVersion.setValue("pmf__c", VaultCollections.asList(pmfRecordId));
      newVersion.setValue("pmf_annotated_version__c", getRedlineDocumentURL(artDocRecordId));

      SaveDocumentVersionsResponse response = documentService.saveDocumentVersions(VaultCollections.asList(newVersion));

      // infinitesimally small chance of a race condition here...
      String newArtDocVersionId = queryService.query(
        "select version_id from documents where id = " + artDocId
      ).streamResults().findFirst().get().getValue("version_id", ValueType.STRING);

      QueryResponse queryResponse = queryService.query(
        "select id from pmf_upversioned_artwork__c where new_artwork_version__c = '"+newArtDocVersionId+"'"
      );
      if (queryResponse.streamResults().count() == 0) {
        attachNewDocVersionToPmf(pmfRecordId, artDocVersionId, newArtDocVersionId, logger);
      }
      return;
    }

    /*
     * Create a new record in object 'PMF Versioned Artwork Document' to link the new Draft version
     * of the Artwork document to the PMF.
     */
    private void attachNewDocVersionToPmf(
      String pmfRecordId, String artDocVersionId, String newArtDocVersionId, JobLogger logger
    ) {

      RecordService recordService = ServiceLocator.locate(RecordService.class);
      QueryService queryService = ServiceLocator.locate(QueryService.class);

      logger.log("attachNewDocVersionToPmf: querying for document number for " + newArtDocVersionId);
      // NOTE: the "old" artDocVersionId is used here because when this query is executed, the new
      // version might not yet exist (because it hasn't been committed to the database).  The "allversions"
      // modifier is used because the new version might exist...
      QueryResponse queryResponse = queryService.query(
        "select document_number__v from allversions documents where version_id = '"+artDocVersionId+"'"
      );
      String docNumber = queryResponse
        .streamResults()
        .findFirst()
        .get()
        .getValue("document_number__v", ValueType.STRING);

      Record record = recordService.newRecord("pmf_upversioned_artwork__c");
      record.setValue("pmf__c", pmfRecordId);
      record.setValue("new_artwork_version__c", newArtDocVersionId);
      record.setValue("document_number__c", docNumber);

      recordService.batchSaveRecords(VaultCollections.asList(record))
        .onErrors(batchOperationErrors -> {
          batchOperationErrors.stream().findFirst().ifPresent(error -> {
            String errMsg = error.getError().getMessage();
            String logMsg =
              "attachNewDocVersionIdToPmf: Unable to create new 'PMF Versioned Artwork Document' record due to: " + errMsg;
            logger.log(logMsg);
            throw new RollbackException("OPERATION_FAILED", logMsg);
          });
        })
        .execute();
    }

    /**
     * Build and return a list of Artwork Document fields where each field's name and ValueType and whether
     * the field is a required field.
     *
     * The information in this list will be used to construct a vql query to query all necessary
     * fields from a PMF's impacted artwork document(s) and to specify the applicable ValueType enum on
     * setting the new DocumentVersion's field values.
     *
     * @return list of VaultField objects
     */
    private List<VaultField> getArtworkDocumentFieldNamesAndTypes() {

      QueryService queryService = ServiceLocator.locate(QueryService.class);
      QueryResponse queryResponse = queryService.query(
        "select LongText(artwork_fields__c) from vproc_parameter_set__c where name__v = 'pmf'"
      );

      String artworkFieldTypesInfo =  queryResponse
        .streamResults()
        .findFirst()
        .get()
        .getValue("artwork_fields__c", ValueType.STRING);

      return Util.parseVaultFieldInfo(artworkFieldTypesInfo);

    }

    private List<String> getFieldNames(List<VaultField> fields) {
      List <String> fieldNames = VaultCollections.newList();
      for (VaultField field : fields) {
        fieldNames.add(field.fieldName);
      }
      return fieldNames;
    }

    /**
     *  Start the "PMF Draft" workflow for the new draft version of the Artwork document.
     * @param artworkDocVersionId
     */
    private void startPmfDraftWorkflow(String artworkDocVersionId, JobLogger logger) {
      String docId = (new DocVersionIdParts(artworkDocVersionId)).id;
      String docOwner = Util.getDocumentOwner(artworkDocVersionId);

      JobService jobService = ServiceLocator.locate(JobService.class);
      JobParameters jobParameters = jobService.newJobParameters("pmf_start_pmf_draft_workflow__c");

      jobParameters.setValue(StartPmfDraftWorkflow.JOB_PARM_DOC_ID, docId);
      jobParameters.setValue(StartPmfDraftWorkflow.JOB_PARM_DOC_OWNER, docOwner);

      logger.log("Running job processor to start 'PMF Draft' workflow for "+docId);

      jobService.runJob(jobParameters);
    }

    // inform the PMF Requestor that an error has occurred while trying to create a new Draft
    private void sendErrorNotification(String pmfRecordId, String artDocVersionId, String reason) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      Record pmfRecord = PMF.getRecord(pmfRecordId);
      Set<String> recipients = VaultCollections.newSet();
      recipients.add(pmfRecord.getValue("pmf_requestor__c", ValueType.STRING));

      String docNumber = queryService.query(
        "select document_number__v from allversions documents where version_id = '"+artDocVersionId+"'"
      ).streamResults().findFirst().get().getValue("document_number__v", ValueType.STRING);

      String url = Util.getDocumentURL(Parameters.getVaultDomain(), artDocVersionId);

      String subject = "Failed to create new draft version of Artwork Document " + docNumber;

      StringBuilder message = new StringBuilder()
        .append("The PMF application was unable to create a new Draft version of ")
        .append("<a href=").append(url).append(">")
        .append(docNumber)
        .append("</a> for ")
        .append("<b>").append(pmfRecord.getValue("name__v", ValueType.STRING)).append("</b>.")
        .append("<br /><br /><b>Reason: </b>").append(reason)
        .append("<br /><br />Contact the system administrator for assistance.");

      Util.sendNotificationSimple(recipients, subject, message.toString());
    }

    private String getRedlineDocumentURL(String pmfArtworkDocumentRecordId) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      QueryResponse queryResponse = queryService.query(
        "select redline_artwork_document__c" +
        "  from pmf_redline_artwork_document__c" +
        " where pmf_artwork_document_record_id__c = '"+pmfArtworkDocumentRecordId+"'"
      );

      if (queryResponse.getResultCount() > 0) {
        String redlineArtworkVersionId = queryResponse
          .streamResults()
          .findFirst()
          .get()
          .getValue("redline_artwork_document__c", ValueType.STRING);
        return Util.getDocumentURL(redlineArtworkVersionId);
      } else {
        return null;
      }
    }

    private String getArtworkVersionState(String artDocVersionId) {
      QueryService queryService = ServiceLocator.locate(QueryService.class);
      return queryService.query(
        "select toName(status__v) from allversions documents where version_id = '"+artDocVersionId+"'"
      )
        .streamResults()
        .findFirst()
        .get()
        .getValue("status__v", ValueType.PICKLIST_VALUES)
        .get(0);
    }

    /*
       The reference constraint on field pmf_artwork_document__c.artwork_document__c allows selection of
       Artwork documents only in certain statuses: "Approved for Use", "Draft", and other statuses that
       reflect the fact that the Artwork content is approved and the Artwork is undergoing Production
       Proofing...
     */
    boolean isProductionProofing(String artworkLifecycleState) {
      return (
        !artworkLifecycleState.equals(ARTWORK_STATE_APPROVED_FOR_USE) &&
        !artworkLifecycleState.equals(ARTWORK_STATE_DRAFT)
      );
    }


// inform the document owner that the new Draft has been created...
// this was replaced by startPmfDraftWorkflow()
//    private void sendNewDraftNotification(String documentVersionId, String newDocumentVersionId) {
//
//      QueryService queryService = ServiceLocator.locate(QueryService.class);
//
//      QueryResult queryResult = queryService.query(
//        "select document_number__v, name__v, id, major_version_number__v, minor_version_number__v " +
//        "from allversions documents where version_id = '"+documentVersionId+"'"
//      ).streamResults().findFirst().get();
//
//      String docNumber = queryResult.getValue("document_number__v", ValueType.STRING);
//      String docName = queryResult.getValue("name__v", ValueType.STRING);
//      String docOwner = Util.getDocumentOwner(documentVersionId);
//      String url = Util.getDocumentURL(newDocumentVersionId);
//
//      String subject = "New draft version created for document "+docNumber+" \""+docName+"\"";
//
//      StringBuilder message = new StringBuilder()
//        .append("<p>")
//        .append("The PMF application has created a new Draft version of ")
//        .append("<b>").append(docNumber).append("</b> ")
//        .append("\"").append(HTML.anchorBlank(url, docName)).append("\".")
//        .append("</p>")
//        .append("<p>Click the link above to access the new Draft, ")
//        .append("update any fields as necessary, and take next steps.</p>");
//
//      Set<String> recipients = VaultCollections.newSet();
//      recipients.add(docOwner);
//
//      Util.sendNotificationSimple(recipients, subject, message.toString());
//    }

  }
