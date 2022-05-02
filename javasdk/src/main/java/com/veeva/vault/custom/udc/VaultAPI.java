package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.http.HttpMethod;
import com.veeva.vault.sdk.api.job.JobLogger;
import com.veeva.vault.sdk.api.json.JsonArray;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonValueType;

import java.math.BigDecimal;
import java.util.List;

/*
 This class contains methods that wrap the Vault API in a convenient way.

 Methods in this class do not throw exceptions.  Successful completion of
 a method is determined by calling the failed() method.

 Example usage:

      VaultAPI vapi = new VaultAPI("pmf_local_connection__c");
      vapi
        .setJobLogger(logger)  // optional
        .addParam("name1", "value1")
        .addParam("name2", "value2")
        .executeUserAction(docVersionId, "expiration_pending_autostart", workflowStartCriteria);
      if (vapi.failed()) {
        ... (String) vapi.getErrorType();
        ... (String) vapi.getErrorMessage();
        ...
      }

 Methods in this class include:
   - cancelWorkflowTasks: initiate workflow actions on one or more workflows - cancel tasks
   - executeQuery: execute a Vault API query
   - initiateDocumentUserAction: execute a document lifecycle user action based on the User Action api name
   - initiateDocumentUserActionLabel: execute a document lifecycle user action based on the User Action label
   - initiateDocumentWorklow: start a workflow for one or more documents (not for legacy workflows)
   - replaceWorkflowOwner: replace the owner of a given active workflow with a new user
 */

@UserDefinedClassInfo()
public class VaultAPI {

  static final String APIVersion = "v21.3";

  private String connection;

  private boolean succeeded;
  private String errorType;
  private String errorMessage;

  private List<HttpParam> params = VaultCollections.newList();
  private Logger logger = new Logger();

  // use localHttpRequest to access the api
  public VaultAPI() { this.connection = null; }

  // Use a connection to access the api
  public VaultAPI(String connection) {
    this.connection = connection;
  }

  public VaultAPI(String connection, JobLogger jobLogger) {
    this.connection = connection;
    this.logger = new Logger(jobLogger);
  }

  public VaultAPI setJobLogger(JobLogger jobLogger) {
    //replace the default Logger with a new Logger that will include job logs
    this.logger = new Logger(jobLogger);
    return this;
  }

  /**
   * Add a body parameter for POST and PUT requests.  Params are cleared out after the completion
   * of each API so that an instance of this object can be used for multiple API calls.  Returns
   * this object instance, so that multiple calls can be chained.
   * @param name
   * @param value
   * @return this object instance
   */
  public VaultAPI addParam(String name, String value) {
    this.params.add(new HttpParam(name, value));
    return this;
  }

  public boolean failed() {
    return !this.succeeded;
  }

  public String getErrorType() {
    return this.errorType;
  }

  public String getErrorMessage() {
    return this.errorMessage;
  }

  /**
   * cancelWorkflowTasks.  Initiate workflow actions on one or more workflows - cancel tasks.
   * Return the initiated Job ID as type 'long'.
   * @param taskIds - List<String> - list of one or more taskIds
   * @return long - initiated Job ID
   */
  public BigDecimal cancelWorkflowTasks(List<String> taskIds) {

    HttpCallout httpCallout = new HttpCallout(this.connection);
    HttpResult httpResult;

    this.succeeded = true;

    String path = "/api/"+APIVersion+"/object/workflow/actions/canceltasks";

    this.params.add(new HttpParam("task_ids", Util.stringifyList(taskIds, "")));

    httpResult = httpCallout.requestJson(HttpMethod.POST, path, this.params, this.logger);

    this.params.clear();  // set up for the next API

    if (httpResult.isError()) {
      this.succeeded = false;
      this.errorType = httpResult.getErrorType();
      this.errorMessage = httpResult.getErrorMessage();
      return null;
    }

    return httpResult
      .getJsonObject()
      .getValue("data", JsonValueType.OBJECT)
      .getValue("job_id", JsonValueType.NUMBER);
  }

  /**
   * initiateDocumentUserActionLabel.  Execute a Document Lifecycle User Action (workflow or state change)
   *   based on the User Action label.
   *
   * @param docVersionId of the document
   * @param actionLabel  label of the action as it appears on the actions menu in the UI
   */
  public void initiateDocumentUserActionLabel(String docVersionId, String actionLabel) {

    DocVersionIdParts docVersionIdParts = new DocVersionIdParts(docVersionId);
    HttpResult httpResult;

    this.succeeded = true;

    StringBuilder path = new StringBuilder();
    path
      .append("/api/").append(APIVersion).append("/objects/documents/")
      .append(docVersionIdParts.id)
      .append("/versions/")
      .append(docVersionIdParts.major)
      .append("/")
      .append(docVersionIdParts.minor)
      .append("/lifecycle_actions");

    HttpCallout httpCallout = new HttpCallout(this.connection);

    httpResult = httpCallout.requestJson(HttpMethod.GET, path.toString(), this.logger);

    if (httpResult.isError()) {
      this.succeeded = false;
      this.errorType = httpResult.getErrorType();
      this.errorMessage = httpResult.getErrorMessage();
      return;
    }

    String actionName = null;

    JsonArray lifecycleActions = httpResult.getJsonObject().getValue("lifecycle_actions__v", JsonValueType.ARRAY);

    for (int i = 0; i < lifecycleActions.getSize(); i++) {
      JsonObject action = lifecycleActions.getValue(i, JsonValueType.OBJECT);
      String label = action.getValue("label__v", JsonValueType.STRING);
      if (label.equals(actionLabel)) {
        actionName = action.getValue("name__v", JsonValueType.STRING);
        break;
      }
    }

    if (actionName == null) {
      this.succeeded = false;
      this.errorType = ErrorType.OPERATION_FAILED;
      this.errorMessage = "An error occurred accessing Vault API \"Retrieve User Actions\".  " +
        "Unable to find action \"" + actionLabel + "\"";
      return;
    }

    path.append("/").append(actionName);

    httpResult = httpCallout.requestJson(HttpMethod.PUT, path.toString(), this.params, this.logger);

    this.params.clear();  // set up for the next API

    if (httpResult.isError()) {
      this.succeeded = false;
      this.errorType = httpResult.getErrorType();
      this.errorMessage = httpResult.getErrorMessage();
    }
  }
  /**
   * initiateDocumentUserActionLabel.  Execute a Document Lifecycle User Action (workflow or state change)
   *   based on the User Action API name.
   *
   * @param docVersionId of the document
   * @param actionName  label of the action as it appears on the actions menu in the UI
   */
  public void initiateDocumentUserAction(String docVersionId, String actionName) {

    DocVersionIdParts docVersionIdParts = new DocVersionIdParts(docVersionId);
    HttpResult httpResult;

    this.succeeded = true;

    StringBuilder path = new StringBuilder();
    path
      .append("/api/").append(APIVersion).append("/objects/documents/")
      .append(docVersionIdParts.id)
      .append("/versions/")
      .append(docVersionIdParts.major)
      .append("/")
      .append(docVersionIdParts.minor)
      .append("/lifecycle_actions/")
      .append(actionName);

    HttpCallout httpCallout = new HttpCallout(this.connection);

    httpResult = httpCallout.requestJson(HttpMethod.PUT, path.toString(), this.params, this.logger);

    this.params.clear();  // set up for the next API

    if (httpResult.isError()) {
      this.succeeded = false;
      this.errorType = httpResult.getErrorType();
      this.errorMessage = httpResult.getErrorMessage();
    }
  }

  /**
   * executeQuery.  Execute a Vault API query.
   *
   * This is needed for queries that are not supported by the JSDK.
   *
   * @param query - String - the query
   */
  public JsonArray executeQuery(String query) {

    HttpCallout httpCallout = new HttpCallout(this.connection);
    HttpResult httpResult;

    this.succeeded = true;

    String path = "/api/"+APIVersion+"/query";

    this.params.add(new HttpParam("q", query));

    httpResult = httpCallout.requestJson(HttpMethod.POST, path.toString(), this.params, this.logger);

    this.params.clear();  // set up for the next API

    if (httpResult.isError()) {
      this.succeeded = false;
      this.errorType = httpResult.getErrorType();
      this.errorMessage = httpResult.getErrorMessage();
      return null;
    }

    return httpResult.getJsonObject().getValue("data", JsonValueType.ARRAY);
  }

  /**
   * repladeWorkflowOwner. Replace the current workflow owner for an active workflow instance with a new user.
   * @param workflowId
   * @param userId
   */
  public void replaceWorklfowOwner(String workflowId, String userId) {

    HttpCallout httpCallout = new HttpCallout(this.connection);
    HttpResult httpResult;

    this.succeeded = true;

    StringBuilder path = new StringBuilder();
    path
      .append("/api/").append(APIVersion).append("/objects/objectworkflows/")
      .append(workflowId)
      .append("/actions/replaceworkflowowner");

    this.params.add(new HttpParam("new_workflow_owner", "user:"+userId));

    httpResult = httpCallout.requestJson(HttpMethod.POST, path.toString(), this.params, this.logger);

    this.params.clear();  // set up for the next API

    if (httpResult.isError()) {
      this.succeeded = false;
      this.errorType = httpResult.getErrorType();
      this.errorMessage = httpResult.getErrorMessage();
    }
  }

  /**
   * initiateDocumentWorkflow.  Start a workflow for one or more documents. Not for legacy workflows.
   * Returns the "data" portion of the JSON response as a JsonObject (see "Initiate Document Workflow" in the
   * API documentation at https://developer.veevavault.com/.
   *
   * Use addParam() to add the needed parameters per the API documentation:
   *   - documents__sys
   *   - participant_name
   *   - description__sys
   *
   * @param workflowName - String. The API name of the workflow, excluding the "Objectworkflow." part.
   * @return
   */
  public JsonObject initiateDocumentWorklow(String workflowName) {

    HttpCallout httpCallout = new HttpCallout(this.connection);
    HttpResult httpResult;

    this.succeeded = true;

    String path = "/api/"+APIVersion+"/objects/documents/actions/Objectworkflow."+workflowName;

    httpResult = httpCallout.requestJson(HttpMethod.POST, path, this.params, this.logger);

    this.params.clear();  // set up for the next API

    if (httpResult.isError()) {
      this.succeeded = false;
      this.errorType = httpResult.getErrorType();
      this.errorMessage = httpResult.getErrorMessage();
      return null;
    }

    return httpResult.getJsonObject().getValue("data", JsonValueType.OBJECT);
  }

}

