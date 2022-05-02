package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.connection.ConnectionContext;
import com.veeva.vault.sdk.api.connection.ConnectionService;
import com.veeva.vault.sdk.api.connection.ConnectionUser;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.document.DocumentRenditionFileReference;
import com.veeva.vault.sdk.api.document.DocumentService;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.document.SaveDocumentVersionsResponse;
import com.veeva.vault.sdk.api.group.Group;
import com.veeva.vault.sdk.api.group.GroupService;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;
import com.veeva.vault.sdk.api.role.DocumentRoleService;
import com.veeva.vault.sdk.api.role.DocumentRoleUpdate;

import java.util.List;

/**
 * RedlineArtwork.
 *
 * This UDC creates a document of type "PMF Redline Artwork" (pmf_redline_artwork__c) and
 * "attaches" it to a PMF by creating a related record in object "PMF Redline Artwork Document"
 * (pmf_redline_artwork_document__c) that contains a reference to the newly created document.
 *
 * If a PMF Redline Artwork document already exists, then re-attach it to the PMF.  This can occur
 * if an Artwork document was linked to a PMF, then removed, then re-linked.  Documents of type
 * "PMF Redline Artwork" are never deleted.
 */

@UserDefinedClassInfo
public class RedlineArtwork {

    /**
     * Create a document of type "PMF Redline Artwork" and attach it to the PMF via a new
     * related record in object "PMF Redline Artwork Document".  The new "PMF Redline Artwork"
     * document is based on an "Artwork" document selected by the user.  The Viewable Rendition
     * of the Artwork document is copied into the newly created document.
     *
     * @param pmfId - the ID of the PMF to which the related
     * @param artDocVersionId - the document version id of the Artwork document from which to
     *                        copy the file for the new Redline Artwork document
     * @param artworkRecordId
     */
    public static void createAndAttach(
        String pmfId,
        String artDocVersionId,
        String artworkRecordId,
        String artworkDocNumber,
        String brand,
        String packagingType,
        String packagingNumber
    ) {

        String redLineDocVersionId = findExistingRedlineDocument(artDocVersionId, pmfId);

        if (redLineDocVersionId == null) {
            redLineDocVersionId = createNewRedlineDocument(artDocVersionId, artworkDocNumber, pmfId);
        }

        // Populate roles on the Redline document so that reviewers can annotate on it ...

        DocumentService documentService = ServiceLocator.locate(DocumentService.class);
        DocumentRoleService docRoleService = ServiceLocator.locate(DocumentRoleService.class);
        GroupService groupService = ServiceLocator.locate(GroupService.class);

        DocumentVersion documentVersion = documentService.newVersionWithId(redLineDocVersionId);

        List<DocumentRoleUpdate> documentRoleUpdateList = VaultCollections.newList();

        Group allInternalUsers = groupService
          .getGroupsByNames(VaultCollections.asList("all_internal_users__v"))
          .getGroupByName("all_internal_users__v");

        DocumentRoleUpdate roleReviewer = docRoleService.newDocumentRoleUpdate("reviewer__v", documentVersion);
        roleReviewer.addGroups(VaultCollections.asList(allInternalUsers));
        documentRoleUpdateList.add(roleReviewer);

        DocumentRoleUpdate roleViewer = docRoleService.newDocumentRoleUpdate("viewer__v", documentVersion);
        roleViewer.addGroups(VaultCollections.asList(allInternalUsers));
        documentRoleUpdateList.add(roleViewer);

        docRoleService.batchUpdateDocumentRoles(documentRoleUpdateList)
          .rollbackOnErrors()
          .execute();

        // Create the new "PMF Redline Artwork Document" object record ...

        RecordService recordService = ServiceLocator.locate(RecordService.class);
        Record r = recordService.newRecord("pmf_redline_artwork_document__c");
        r.setValue("pmf__c", pmfId);
        r.setValue("redline_artwork_document__c", redLineDocVersionId);
        r.setValue("pmf_artwork_document_record_id__c", artworkRecordId);
        r.setValue("artwork_document_number__c", artworkDocNumber);
        r.setValue("brand__c", brand);
        r.setValue("packaging_type__c", packagingType);
        r.setValue("packaging_number__c", packagingNumber);

        recordService.batchSaveRecords(VaultCollections.asList(r))
          .onErrors(batchOperationErrors -> {
              batchOperationErrors.stream().findFirst().ifPresent(error -> {
                  String errMsg = error.getError().getMessage();
                  throw new RollbackException(
                    "OPERATION_FAILED", "Unable to attach Redline Artwork document due to: " + errMsg
                  );
              });
          })
          .execute();

  }

    private static String findExistingRedlineDocument(String artworkDocVersionId, String pmfId) {

        QueryService queryService = ServiceLocator.locate(QueryService.class);
        String query =
          "select version_id from documents " +
            "where toName(type__v) = 'pmf_redline_artwork__c' " +
            "and artwork_version_id__c = '" + artworkDocVersionId + "' " +
            "and pmf__c = '" + pmfId + "'";

        QueryResponse qResponse = queryService.query(query);

        if (qResponse.getResultCount() == 0) {
            return null;
        }

        return qResponse.streamResults().iterator().next().getValue("version_id", ValueType.STRING);
    }

    private static String createNewRedlineDocument(String artworkDocVersionId, String artworkDocNumber, String pmfId) {

        ConnectionService connectionService = ServiceLocator.locate(ConnectionService.class);
        ConnectionContext connectionContext = connectionService.newConnectionContext(
          "pmf_local_connection__c", ConnectionUser.SDK_CURRENT_USER   //CONNECTION_AUTHORIZED_USER
        );
        DocumentService documentService = ServiceLocator.locate(DocumentService.class);
        QueryService queryService = ServiceLocator.locate(QueryService.class);

        String query = "select name__v from documents where version_id = '" + artworkDocVersionId + "'";
        QueryResult qr = queryService.query(query).streamResults().iterator().next();
        String artworkName = qr.getValue("name__v", ValueType.STRING);

        String redlineArtworkName = "Redline: " + artworkName;
        if (redlineArtworkName.length() > 100) {
            redlineArtworkName = redlineArtworkName.substring(0, 97) + "..."; // keep within vault limits for name__v
        }

        DocumentRenditionFileReference artworkViewableRendition =
          documentService.newDocumentRenditionFileReference(
            connectionContext, artworkDocVersionId, "viewable_rendition__v"
          );
        DocumentVersion documentVersion = documentService.newDocument();

        documentVersion.setValue("type__v", VaultCollections.asList("PMF Redline Artwork"));
        documentVersion.setValue("lifecycle__v", VaultCollections.asList("PMF Redline Artwork"));
        documentVersion.setValue("name__v", redlineArtworkName);
        documentVersion.setValue("artwork_version_id__c", artworkDocVersionId);
        documentVersion.setValue("pmf__c", VaultCollections.asList(pmfId));
        documentVersion.setValue("owner__v", VaultCollections.asList(PMF.getRequestor(pmfId)));
        documentVersion.setValue("title__v",
          "This document was created by the System for the purpose of redlining artwork document " + artworkDocNumber
        );
        documentVersion.setSourceFile(artworkViewableRendition);

        SaveDocumentVersionsResponse response = documentService.createDocuments(VaultCollections.asList(documentVersion));
        String documentVersionId = response.getSuccesses().get(0).getDocumentVersionId();

        return documentVersionId;
    }

}