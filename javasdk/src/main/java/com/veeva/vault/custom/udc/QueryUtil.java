package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.query.*;

/*

  This class wraps QueryService calls in a way that makes it simple to handle VQL Queries based on the new interfaces 
  introduced at 22R1.,

  Static methods in this class:

  query - Execute a query, and return the resulting QueryExecutionResponse object.
  queryOne - Return a single QueryExecutionResult, or null if the query returns no result.
  queryCount - Return a long integer containing the count of rows that would be returned by the query.

 */

@UserDefinedClassInfo
public class QueryUtil {
    
    /**
     * Execute a query, and return the resulting QueryExecutionResponse object.
     * @param query -- String.  The vql query string.
     * @return  -- and instance of QueryExecutionResponse
     */
    public static QueryExecutionResponse query(String query) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      QueryExecutionResponse[] queryResponse = {null};

      QueryExecutionRequest qeRequest = queryService.newQueryExecutionRequestBuilder()
        .withQueryString(query)
        .build();
      queryService.query(qeRequest)
        .onSuccess(response -> {
          queryResponse[0] = response;
        })
        .onError(error -> {
          throw new RollbackException(ErrorType.OPERATION_FAILED, error.getMessage());
        })
        .execute();

      return queryResponse[0];
    }

    /**
     * queryOne.  Return a single QueryExecutionResult, or null if the query returns no result.
     * @param query
     * @return
     */
    public static QueryExecutionResult queryOne(String query) {
      QueryExecutionResult queryExecutionResult = null;
      QueryExecutionResponse queryExecutionResponse = QueryUtil.query(query);
      if (queryExecutionResponse.getResultCount() > 0) {
        queryExecutionResult = queryExecutionResponse.streamResults().findFirst().get();
      }
      return queryExecutionResult;
    }

    /**
     * queryCount.  Return a long integer containing the count of rows that would be
     * returned by the query.
     * @param query
     * @return
     */
    public static long queryCount(String query) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      QueryCountRequest queryCountRequest = queryService.newQueryCountRequestBuilder()
        .withQueryString(query)
        .build();

      long count[] = {0};

      queryService.count(queryCountRequest)
        .onSuccess(queryCountResponse -> {
          count[0] = queryCountResponse.getTotalCount();
        })
        .onError(error -> {
          throw new RollbackException(ErrorType.OPERATION_FAILED, error.getMessage());
        })
        .execute();

      return count[0];
    }

}