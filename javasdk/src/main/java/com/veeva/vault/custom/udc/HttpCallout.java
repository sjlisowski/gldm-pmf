package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.http.HttpMethod;
import com.veeva.vault.sdk.api.http.HttpRequest;
import com.veeva.vault.sdk.api.http.HttpResponseBodyValueType;
import com.veeva.vault.sdk.api.http.HttpService;
import com.veeva.vault.sdk.api.json.JsonArray;
import com.veeva.vault.sdk.api.json.JsonData;
import com.veeva.vault.sdk.api.json.JsonValueType;

import java.util.List;

/**
 *  Wrapper for HttpService, to make it easy to use the service.
 *
 *  Can make requests with a Connection, or local requests without.
 */

@UserDefinedClassInfo
public class HttpCallout {

  private String connectionName;

  public HttpCallout() {
    this.connectionName = null;
  }

  public HttpCallout(String connectionName) {
    this.connectionName = connectionName;
  }

  /**
   * Make an HTTP request that returns JSON.
   *
   * @param method - HttpMethod
   * @param path   - String. url path
   * @param params - List.
   */
  public HttpResult requestJson(HttpMethod method, String path, List<HttpParam> params, Logger logger) {

    HttpService httpService = ServiceLocator.locate(HttpService.class);

    HttpResult httpResult = new HttpResult();

    HttpRequest request;

    if (this.connectionName == null) {
      request = httpService.newLocalHttpRequest();
    } else {
      request = httpService.newHttpRequest(this.connectionName);
    }

    request
      .setMethod(method)
      .appendPath(path);

    if (params != null) {
      for (HttpParam param : params) {
        request.setBodyParam(param.name, param.value);
      }
    }

    httpService.send(request, HttpResponseBodyValueType.JSONDATA)
      .onSuccess(httpResponse -> {
        int responseCode = httpResponse.getHttpStatusCode();
        logger.info("RESPONSE: " + responseCode);

        JsonData response = httpResponse.getResponseBody();
        logger.info("RESPONSE: " + response);

        if (response.isValidJson()) {
          String responseStatus = response.getJsonObject().getValue("responseStatus", JsonValueType.STRING);

          if (responseStatus.equals("SUCCESS")) {
            httpResult.setData(response.getJsonObject());
          } else {
            if (response.getJsonObject().contains("responseMessage") == true) {
              String responseMessage = response.getJsonObject().getValue("responseMessage", JsonValueType.STRING);
              logger.error("ERROR: " + responseMessage);
              httpResult.setError(ErrorType.OPERATION_FAILED, responseMessage);
            }
            if (response.getJsonObject().contains("errors") == true) {
              JsonArray errors = response.getJsonObject().getValue("errors", JsonValueType.ARRAY);
              String type = errors.getValue(0, JsonValueType.OBJECT).getValue("type", JsonValueType.STRING);
              String message = errors.getValue(0, JsonValueType.OBJECT).getValue("message", JsonValueType.STRING);
              logger.error("ERROR "+type+": " + message);
              httpResult.setError(type, message);
            }
          }
        }
      })
      .onError(httpOperationError -> {
        int responseCode = httpOperationError.getHttpResponse().getHttpStatusCode();
        logger.info("RESPONSE: " + responseCode);
        logger.info(httpOperationError.getMessage());
        logger.info(httpOperationError.getHttpResponse().getResponseBody());
        httpResult.setError(ErrorType.OPERATION_FAILED, httpOperationError.getMessage());
      })
      .execute();

    return httpResult;
  }

  public HttpResult requestJson(HttpMethod method, String path, Logger logger) {
    List<HttpParam> params = VaultCollections.newList();
    return this.requestJson(method, path, params, logger);
  }

  // Static methods provided for convenience.  These methods use local connection without a Connection record.
  public static HttpResult localRequestJson(HttpMethod method, String path, List<HttpParam> params, Logger logger) {
    HttpCallout httpCallout = new HttpCallout();
    return httpCallout.requestJson(method, path, params, logger);
  }
  public static HttpResult localRequestJson(HttpMethod method, String path, Logger logger) {
    List<HttpParam> params = VaultCollections.newList();
    return localRequestJson(method, path, params, logger);
  }



}