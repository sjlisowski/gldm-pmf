package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;

/**
 * Simple storage mechanism for HTTP request parameters.
 */

@UserDefinedClassInfo
public class HttpParam {
    public String name;
    public String value;

    /**
      HttpParam constructor.
      @param name - the name of the parameter
      @param value - the value of the parameter
     */
    public HttpParam(String name, String value) {
      this.name = name;
      this.value = value;
    }
}