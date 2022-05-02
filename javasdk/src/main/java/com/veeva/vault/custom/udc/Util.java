package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.document.DocumentService;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.notification.NotificationMessage;
import com.veeva.vault.sdk.api.notification.NotificationParameters;
import com.veeva.vault.sdk.api.notification.NotificationService;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;
import com.veeva.vault.sdk.api.role.DocumentRole;
import com.veeva.vault.sdk.api.role.DocumentRoleService;
import com.veeva.vault.sdk.api.role.GetDocumentRolesResponse;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
  Static methods in this class:

  jskdDataTypeMap - Return the Vault JSDK Field Type map, keyed by the Vault Field Type (e.g. "String") where
     the values are the JSDK ValueType object.
  vqlContains - Return a String containing a VQL 'contains' filter surrounded by parenthises, e.g.:
     "('this', 'that', 'the other')".
  stringifyList - Return a comma-delimited string built from a List of Strings.
  stringifySet - Return a comma-delimited string built from a KeySet of Strings.
  getFirst - Return the first item in a list of Strings.  Return null if the list is null.
  getRecordID - Return the ID of a record where the identified field contains the identified value.
  getRecordValue - Return a field value from an Object Record identified by the Record's ID.
  getTypeName - Return the API name of an object record's Object Type.
  getObjectTypeName - Return the Object Type name for the record.  Assumes the values passed as arguments are valid.
  getRoleId - Return the object record ID from the Application Role object where the record is for the Regulatory role.
  getUSCountryId - Return the record ID from the Country Object record for the United States.
  getSinglePicklistValue - Return the value from a single-pick picklist field, or null if the field value is null.
  stringifyFieldValues - Concatenates a field value across one or more records in a query response.
  parseVaultFieldInfo - return a list of VaultField objects parsed from the input string
  difference - Return a list of Strings from list1/set1 that are not also in list2/set2.
  toList - Convert a collection (e.g. Set) to a List, and return the List.
  getDocumentRoleUsers - return a list of User Ids of users currently in the document's indicated role
  getDocumentOwner - Return the UserID of the indicated document's Owner
  getObjectRecordURL - return a String containing the Vault UI URL for an object record
  getDocumentURL - return a String containing the Vault UI URL for a document
  sendNotificationSimple - Send a simple notification email
  getUserFullName - Return the full name for a user based on the User's user ID.
  isVaultOwner - Return a boolean value to indicate whether a user has the Vault Owner Security Profile.
 */

@UserDefinedClassInfo
public class Util {

  /**
   * Return the Vault JSDK Field Type map, keyed by the Vault Field Type (e.g. "String") where
   * the values are the JSDK ValueType object.
   * @return
   */
  public static Map<String, ValueType> jsdkDataTypeMap() {

    Map<String, ValueType> map = VaultCollections.newMap();

    map.put("STRING", ValueType.STRING);
    map.put("BOOLEAN", ValueType.BOOLEAN);
    map.put("NUMBER", ValueType.NUMBER);
    map.put("DATE", ValueType.DATE);
    map.put("DATETIME", ValueType.DATETIME);
    map.put("PICKLIST_VALUES", ValueType.PICKLIST_VALUES);
    map.put("REFERENCES", ValueType.REFERENCES);

    return map;
  }

  /**
   * Return a String containing a VQL 'contains' filter surrounded by parenthises, e.g.:
   *      "('this', 'that', 'the other')".  The list is assumed to contain elements.
   * @param list - List<String> list it items to be included in the 'contains' filter.
   * @return String - the 'contains' filter.
   */
  public static String vqlContains(List<String> list) {

    StringBuilder contains = new StringBuilder();
    Iterator<String> iter = list.iterator();

    contains.append("(");
    while (iter.hasNext()) {
      contains.append("'").append(iter.next()).append("'");
      if (iter.hasNext()) {
        contains.append(",");
      }
    }
    contains.append(")");

    return contains.toString();
  }

  /**
   * Return a comma-delimited string built from a List of strings.
   * @param list - List<String>.  the list
   * @param separator - String. Optional.  What should separate the individual items
   *                    in the resulting string.  Default value is ", ".
   * @return String
   */
  public static String stringifyList(List<String> list, String separator) {

    StringBuilder sb = new StringBuilder();

    Iterator<String> iter = list.iterator();

    while (iter.hasNext()) {
      sb.append(iter.next());
      if (iter.hasNext()) {
        sb.append(separator);
      }
    }

    return sb.toString();
  }
  public static String stringifyList(List<String> list) {
    return Util.stringifyList(list, ", ");
  }

  /**
   * Return a comma-delimitred string built from a KeySet of Strings.
   * @param set
   * @return
   */
  public static String stringifySet(Set<String> set) {
    List<String> list = VaultCollections.newList();
    list.addAll(set);
    return stringifyList(list);
  }

  /**
   * Return the first item in a list of Strings.  Return null if the list is null.
   * @param list - List<String> - list of Strings (can be null)
   * @return
   */
  public static String getFirst(List<String> list) {
    if (list == null) {
      return null;
    } else {
      return list.get(0);
    }
  }

  /**
   * Return the ID of a record where the identified field contains the identified value.  fieldName should
   * be the name of a unique Text field on the Object.  If the Text field is not unique, the ID of the
   * first record is returned.
   * @param objectName - String.  Name of the Vault Object.
   * @param fieldName - String.  Name of the field on the Vault Object to be queried.
   * @param fieldValue - String.  The value to use as a filter for the field in the query.
   * @return String.  ID of record.
   */
  public static String getRecordID(String objectName, String fieldName, String fieldValue) {
    QueryService qs = ServiceLocator.locate(QueryService.class);
    QueryResponse qr = qs.query("select id from "+objectName+" where "+fieldName+" = '"+fieldValue+"'");
    return qr.streamResults().iterator().next().getValue("id", ValueType.STRING);
  }

  /**
   * Return a field value from a record.  Assumes the value of recordID is a valid ID of a Record
   * that currently exists in the Object.
   * @param objectName - String.  Name of the Vault Object.
   * @param fieldName - String.  Name of the Field on the Vault Object.
   * @param recordID - String.  ID of the Object Record to select.
   * @param valueType - ValueType<T>. ValueType of the field value.
   * @return <T> T - Field value to return
   */
    public static <T> T getRecordValue(String objectName, String fieldName, String recordID, ValueType<T> valueType) {
      QueryService qs = ServiceLocator.locate(QueryService.class);
      QueryResponse qr = qs.query("select "+fieldName+" from "+objectName+" where id = '"+recordID+"'");
      return qr.streamResults().iterator().next().getValue(fieldName, valueType);
    }

  /**
   * Return the API name of an object record's Object Type.
   * @param objectTypeID - from the record's object_type__v field value.
   * @return String - api name of the object type
   */
    public static String getTypeName(String objectTypeID) {

      QueryService qs = ServiceLocator.locate(QueryService.class);
      String query = "select api_name__v from object_type__v where id = '"+objectTypeID+"'";
      QueryResponse qr = qs.query(query);
      return qr.streamResults().iterator().next().getValue("api_name__v", ValueType.STRING);
    }

   /**
     * Return the API name of an object record's Object Type.
     * @param record - Object record
     * @return String - api name of the object type
     */
    public static String getTypeName(Record record) {
      return Util.getTypeName(record.getValue("object_type__v", ValueType.STRING));
    }

    /**
     * Return the Object Type name for the record.  Assumes the values passed as
     * arguments are valid.
     * @param objectName - api name of the object
     * @param recordId - id of the record
     * @return id
     */
    public static String getObjectTypeName(String objectName, String recordId) {
      QueryService queryService = ServiceLocator.locate(QueryService.class);
      QueryResponse queryResponse = queryService.query(
        "select object_type__vr.api_name__v" +
        "  from " +objectName +
        " where id = '"+recordId+"'"
      );
      return queryResponse
              .streamResults()
              .findFirst()
              .get()
              .getValue("object_type__vr.api_name__v", ValueType.STRING);
    }

  /**
   * Return the object record ID from the Application Role object where the record is for the Regulatory role.
   * @return
   */
    public static String getRoleId(String roleName) {
      QueryService qs = ServiceLocator.locate(QueryService.class);
      QueryResponse qr = qs.query("select id from application_role__v where api_name__v = '"+roleName+"'");
      return qr.streamResults().iterator().next().getValue("id", ValueType.STRING);
    }

  /**
   * Return the record ID from the Country Object record for the United States.
   * @return String. record ID
   */
    public static String getUSCountryId() {
      QueryService qs = ServiceLocator.locate(QueryService.class);
      QueryResponse qr = qs.query("select id from country__v where abbreviation__c = 'US'");
      return qr.streamResults().iterator().next().getValue("id", ValueType.STRING);
    }

  /**
   * Return the value from a single-pick picklist field, or null if the field value is null.
   * @param values - a value returned from .getValue(fieldname, ValueType.PICKLIST_VALUES)
   * @return String - the 1st value in the picklist, or null
   */
    public static String getSinglePicklistValue(List<String> values) {
      if (values == null) {
        return null;
      } else {
        return values.get(0);
      }
    }

    /**
     * stringifyFieldValues.  Concatenates a field value across one or more records in a query response
     * and returns the field values as a delimited string.
     *
     * @param qResponse - a QueryResponse object
     * @param fieldName - the field name from which to pull values
     * @param fieldLength - the maximum length of the String to return
     * @param delimiter - a string to use to delimit the values
     * @return a String containing the field values delimited by the provided delimiter
     */
    public static String stringifyFieldValues(
            QueryResponse qResponse, String fieldName, int fieldLength, String delimiter
    ) {
        StringBuilder stringBuilder = new StringBuilder(fieldLength);

        Iterator<QueryResult> iterator = qResponse.streamResults().iterator();

        while (iterator.hasNext()) {
            QueryResult qr = iterator.next();
            String value = qr.getValue(fieldName, ValueType.STRING);
            if (value != null) {
              stringBuilder.append(value);
              if (iterator.hasNext()) {
                stringBuilder.append(delimiter);
              }
            }
        }

        String fieldString = stringBuilder.toString();

        return fieldString.length() > fieldLength ? fieldString.substring(0, fieldLength) : fieldString;
    }

    /**
     *  Return a list of VaultField objects parsed from the input string.
     *
     *  The input String is expected to contain one or more lines each containing
     *  information about a Vault Document Field.  See class VaultField for details
     *  on the contents of each line.
     *
     *  Lines beginning with # are comment lines, and are ignored.
     *  Blank lines are ignored.
     *
     * @param fieldInfo
     * @return
     */
    public static List<VaultField> parseVaultFieldInfo(String fieldInfo) {

      List<VaultField> vaultFields = VaultCollections.newList();

      String [] lines = StringUtils.split(fieldInfo, "\n");

      for (String line : lines) {

        line = line.trim();

        if (line.length() == 0) {
          continue;  // allow blank lines
        }
        if (line.charAt(0) == '#') {
          continue;  // comment line
        }

        vaultFields.add(VaultField.parse(line));
      }

      return vaultFields;
    }

    /**
     * Return a list of Strings from list1 that are not also in list2.
     * @param list1
     * @param list2
     * @return List<String>
     */
    public static List<String> difference(List<String> list1, List<String> list2) {

      List<String> result = VaultCollections.newList();
      Iterator<String> iter1 = list1.iterator();

      while (iter1.hasNext()) {

        String item1 = iter1.next();
        Iterator<String> iter2 = list2.iterator();
        boolean found = false;

        while (iter2.hasNext()) {
          String item2 = iter2.next();
          if (item1.equals(item2)) {
            found = true;
            break;
          }
        }

        if (!found) {
          result.add(item1);
        }
      }

      return result;
    }

  /**
   * Return a list of Strings from set1 that are not also in set2.
   * @param set1 - Set<String>
   * @param set2 - Set<String>
   * @return List<String>
   */
    public static List<String> difference(Set<String> set1, Set<String> set2) {
      return difference(toList(set1), toList(set2));
    }

  /**
   * Return a List<String> containing the elements of a Key<String>
   * @param set
   * @return list
   */
    public static List<String> toList(Set<String> set) {
      List<String> list = VaultCollections.newList();
      for (String item : set) {
        list.add(item);
      }
      return list;
    }

  /**
   * Return a list of User Ids of users currently in the document's indicated role
   * @param docVersion - instance of Document Version
   * @param roleName - String api name of role
   * @return List<String> - collection of User Id's
   */
    public static List<String> getDocumentRoleUsers(DocumentVersion docVersion, String roleName) {
      DocumentRoleService documentRoleService = ServiceLocator.locate(DocumentRoleService.class);
      GetDocumentRolesResponse rolesResponse = documentRoleService.getDocumentRoles(
        VaultCollections.asList(docVersion), roleName
      );
      DocumentRole documentRole = rolesResponse.getDocumentRole(docVersion);
      return documentRole.getUsers();
    }

  /**
   * Return a list of User Ids of users currently in the document's indicated role
   * @param documentVersionId - String version id of document
   * @param roleName - String api name of role
   * @return List<String> - collection of User Id's
   */
    public static List<String> getDocumentRoleUsers(String documentVersionId, String roleName) {
      DocumentService documentService = ServiceLocator.locate(DocumentService.class);
      DocumentVersion docVersion = documentService.newVersionWithId(documentVersionId);
      return getDocumentRoleUsers(docVersion, roleName);
    }

  /**
   * Return the UserID of the indicated document's Owner
   * @param docVersion - instance of DocumentVersion
   * @return String - User Id
   */
    public static String getDocumentOwner(DocumentVersion docVersion) {
      return getDocumentRoleUsers(docVersion, "owner__v").get(0);
    }

  /**
   * Return the UserID of the indicated document's Owner
   * @param documentVersionId - String - version id of Document
   * @return String - User Id
  */
    public static String getDocumentOwner(String documentVersionId) {
      return getDocumentRoleUsers(documentVersionId, "owner__v").get(0);
    }

    /**
     * Return a Vault UI URL to the Object Record in the format:
     *    e.g.: https://sb-galderma-galderma-sandbox.veevavault.com/ui/#object/pmf__c/V4400000000A001
     * @param vaultDomain - the domain part of the Vault's URL
     * @param objectName - name of the Object whose record will be displayed
     * @param recordID
     * @return
     */
    public static String getObjectRecordURL(String vaultDomain, String objectName, String recordID) {
      StringBuilder sbURL = new StringBuilder();
      sbURL
        .append("https://")
        .append(vaultDomain)
        .append("/ui/#object/")
        .append(objectName).append("/")
        .append(recordID);
      return sbURL.toString();
    }
    public static String getObjectRecordURL(String objectName, String recordID) {
      return getObjectRecordURL(Parameters.getVaultDomain(), objectName, recordID);
    }

  /**
   * Return a Vault UI URL to the Document in the format:
   *    e.g.: https://sb-galderma-galderma-sandbox.veevavault.com/ui/#doc_info/1/0/0
   * @param vaultDomain - the domain part of the Vault's URL
   * @param docVersionId - name of the Object whose record will be displayed
   * @return
   */
  public static String getDocumentURL(String vaultDomain, String docVersionId) {

    DocVersionIdParts docVersionIdParts = new DocVersionIdParts(docVersionId);

    StringBuilder sbURL = new StringBuilder();
    sbURL
      .append("https://")
      .append(vaultDomain)
      .append("/ui/#doc_info/")
      .append(docVersionIdParts.id).append("/")
      .append(docVersionIdParts.major).append("/")
      .append(docVersionIdParts.minor);

    return sbURL.toString();
  }

  /**
   * Return a Vault UI URL to the Document in the format:
   *    e.g.: https://sb-galderma-galderma-sandbox.veevavault.com/ui/#doc_info/1/0/0
   * @param docVersionId - name of the Object whose record will be displayed
   * @return
   */
  public static String getDocumentURL(String docVersionId) {
    return getDocumentURL(Parameters.getVaultDomain(), docVersionId);
  }

    /**
     * Send a simple notification email.
     * @param recipients
     * @param message
     */
    public static void sendNotificationSimple(Set<String> recipients, String subject, String message) {

      NotificationService notificationService = ServiceLocator.locate(NotificationService.class);

      String body =
        "<p>${recipientName}.</p>" +
        "<p>${notificationMessage}</p>" +
        "<p>Access your Vault here: <a href=\"${uiBaseExtUrl}\">${vaultName}</a></p>";

      NotificationMessage notificationMessage = notificationService.newNotificationMessage()
        .setNotificationText(message)
        .setMessage(body)
        .setSubject(subject);

      NotificationParameters notificationParameters = notificationService.newNotificationParameters();
      notificationParameters.setRecipientsByUserIds(recipients);

      notificationService.send(notificationParameters, notificationMessage);
    }

    /**
     * Return the full name for a user based on the User's user ID.
     * @param userId - String. User's user ID
     * @return String. User's full name
     */
    public static String getUserFullName(String userId) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      QueryResponse queryResponse = queryService.query(
        "select name__v from user__sys where id = " + userId
      );

      return queryResponse.streamResults().findFirst().get().getValue("name__v", ValueType.STRING);

    }

    /**
     * Return a boolean value to indicate whether a user has the Vault Owner Security Profile.
     * This method assumes that the userId is valid.
     *
     * @param userId - user id of the user in question.
     * @return boolean.  True if is a Vault Owner.
     */
    public static boolean isVaultOwner(String userId) {

      QueryService queryService = ServiceLocator.locate(QueryService.class);

      QueryResponse queryResponse = queryService.query(
        "select security_profile__sysr.profile_key__sys from user__sys where id = '"+userId+"'"
      );

      String profileKey = queryResponse
        .streamResults()
        .findFirst()
        .get()
        .getValue("security_profile__sysr.profile_key__sys", ValueType.STRING);

      return profileKey.equals("vaultOwner");
    }

}

