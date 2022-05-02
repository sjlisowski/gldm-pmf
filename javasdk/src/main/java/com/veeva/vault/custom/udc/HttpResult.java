package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.json.JsonObject;

/**
 * Simple class for passing the results of an HttpCallout to the caller.
 */

@UserDefinedClassInfo
public class HttpResult {

    public String errorType;
    public String errorMessage;
    public Object data;

    public HttpResult() {
      this.errorType = null;
      this.errorMessage = null;
      this.data = null;
    }

    protected void setError(String errorType, String errorMessage) {
      this.errorType = errorType == null ? "UNDEFINED_ERROR" : errorType;
      this.errorMessage = errorMessage == null ? "Undefined error" : errorMessage;
    }

    protected void setData(Object data) {
      this.data = data;
    }

    public boolean isError() {
      return errorType != null || errorMessage != null;
    }

    public String getErrorType() {
      return this.errorType;
    }

    public String getErrorMessage() {
      return this.errorMessage;
    }

    public JsonObject getJsonObject() {
      return (JsonObject) data;
    }

}