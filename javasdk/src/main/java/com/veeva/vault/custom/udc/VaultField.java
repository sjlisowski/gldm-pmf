package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.StringUtils;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.ValueType;

/**
 * Stores metadata information about a Vault Object or Document Field.  This simple structure can be
 * used to build a query and/or populate a Record or DocumentVersion object.
 */

@UserDefinedClassInfo
public class VaultField {

  public String fieldName;
  public ValueType valueType;
  public boolean required;

  public VaultField(String fieldName, ValueType valueType, boolean required) {
    this.fieldName = fieldName;
    this.valueType = valueType;
    this.required = required;
  }

  // for convenience...
  public VaultField(String fieldName, ValueType valueType) {
    this.fieldName = fieldName;
    this.valueType = valueType;
    this.required = false;
  }

  /**
   * Return an instance of VaultField parsed from the input string.  The input String
   * must be in the format:
   *
   *    <api field name>|<data type>[|"required"]
   *
   * Possible data types include:
   *
   *   - "ObjectReference", for fields that reference an Object, such as Brand
   *   - "String", for text fields
   *   - "Picklist", for picklist fields
   *   - "Boolean", for Yes/No fields
   *   - "Date", for date fields
   *   - "Number", for number fields
   *
   * Some examples:
   *
   *    1) approval_deadline__c|Date|required
   *    2) barcode_number__c|String
   *    3) barcode_type__c|Picklist
   *
   * @param fieldInfo - String
   */
  static public VaultField parse(String fieldInfo) {

    final String errorText = "Invalid Vault field definition string: '" + fieldInfo + "': ";

    String[] elements = StringUtils.split(fieldInfo, "\\|");

    if (elements.length < 2 || elements.length > 3) {
      throw new RollbackException(ErrorType.OPERATION_DENIED, errorText + "wrong number of elements");
    }

    String fieldName;
    ValueType valueType;
    boolean required;

    fieldName = elements[0];

    if (elements[1].equals("ObjectReference")) {
      valueType = ValueType.REFERENCES;
    } else if (elements[1].equals("String")) {
      valueType = ValueType.STRING;
    } else if (elements[1].equals("Picklist")) {
      valueType = ValueType.PICKLIST_VALUES;
    } else if (elements[1].equals("Boolean")) {
      valueType = ValueType.BOOLEAN;
    } else if (elements[1].equals("Date")) {
      valueType = ValueType.DATE;
    } else if (elements[1].equals("Number")) {
      valueType = ValueType.NUMBER;
    } else if (elements[1].equals("DateTime")) {
      valueType = ValueType.DATETIME;
    } else {
      throw new RollbackException(ErrorType.OPERATION_DENIED, errorText + "invalid field type");
    }

    if (elements.length > 2 && elements[2].length() > 0) {
      if (elements[2].equals("required")) {
        required = true;
      } else {
        throw new RollbackException(ErrorType.OPERATION_DENIED, errorText + "invalid required attribute");
      }
    } else {
      required = false;
    }

    return new VaultField(fieldName, valueType, required);
  }
  
}