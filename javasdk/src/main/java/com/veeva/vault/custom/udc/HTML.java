package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;

/**
 * Contains static methods to support building HTML documents
 */

@UserDefinedClassInfo
public class HTML {

  /**
   * Return an anchor element with target="_blank"
   * @param url - String
   * @param text - String
   * @return
   */
    public static String anchorBlank(String url, String text) {
      return new StringBuilder()
        .append("<a href=\"").append(url).append("\" target=\"_blank\">")
        .append(text)
        .append("</a>")
        .toString();
    }
}