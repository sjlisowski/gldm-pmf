package com.veeva.vault.custom.actions.test;

import com.veeva.vault.custom.udc.HTML;
import com.veeva.vault.custom.udc.Util;
import com.veeva.vault.sdk.api.action.DocumentAction;
import com.veeva.vault.sdk.api.action.DocumentActionContext;
import com.veeva.vault.sdk.api.action.DocumentActionInfo;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;
import com.veeva.vault.custom.udc.Parameters;

import java.math.BigDecimal;
import java.util.Set;

@DocumentActionInfo(label="Document Tester")
public class DocumentTester implements DocumentAction {

    public boolean isExecutable(DocumentActionContext context) {
        return true;
    }

    public void execute(DocumentActionContext context) {

//      DocumentVersion docVersion = context.getDocumentVersions().get(0);
//
//      String id = docVersion.getValue("id", ValueType.STRING);
//      BigDecimal major = docVersion.getValue("major_version_number__v", ValueType.NUMBER);
//      BigDecimal minor = docVersion.getValue("minor_version_number__v", ValueType.NUMBER);
//
//      sendNewDraftNotification(id + "_" + major + "_" + minor);

      VaultInformationService vaultInformationService = ServiceLocator.locate(VaultInformationService.class);
      VaultInformation vaultInformation = vaultInformationService.getLocalVaultInformation();
      String vaultDns1 = vaultInformation.getDns();
      String vaultDns2 = Parameters.getVaultDomain();


      int stop=0;
    }

  private void sendNewDraftNotification(String documentVersionId) {

    QueryService queryService = ServiceLocator.locate(QueryService.class);

    QueryResult queryResult = queryService.query(
      "select document_number__v, name__v, id, major_version_number__v, minor_version_number__v " +
      "from allversions documents where version_id = '"+documentVersionId+"'"
    ).streamResults().findFirst().get();

    String docNumber = queryResult.getValue("document_number__v", ValueType.STRING);
    String docName = queryResult.getValue("name__v", ValueType.STRING);
    String id = queryResult.getValue("id", ValueType.STRING);
    BigDecimal major = queryResult.getValue("major_version_number__v", ValueType.NUMBER);
    BigDecimal minor = queryResult.getValue("minor_version_number__v", ValueType.NUMBER);
    String docOwner = Util.getDocumentOwner(documentVersionId);
    String url = Util.getDocumentURL(documentVersionId);

    String subject = "New draft version created for document "+docNumber+" \""+docName+"\"";

    StringBuilder message = new StringBuilder()
      .append("<p>")
      .append("The PMF application has created a new Draft version of ")
      .append("<b>").append(docNumber).append("</b> ")
      .append("\"").append(HTML.anchorBlank(url, docName)).append("\".")
      .append("</p>")
      .append("<p>Click the link above to access the new Draft, update any fields as necessary, and take next steps.</p>");

    Set<String> recipients = VaultCollections.newSet();
    recipients.add(docOwner);

    Util.sendNotificationSimple(recipients, subject, message.toString());
  }

}