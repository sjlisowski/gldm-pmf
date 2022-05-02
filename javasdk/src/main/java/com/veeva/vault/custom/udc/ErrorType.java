package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;

/**
 * What this does...
 */

@UserDefinedClassInfo
public class ErrorType {
    public static final String OPERATION_FAILED = "OPERATION_FAILED";
    public static final String UPDATE_DENIED = "UPDATE_DENIED";
    public static final String INVALID_DOCUMENT = "INVALID_DOCUMENT";
    public static final String DUPLICATE_DOCUMENT = "DUPLICATE_DOCUMENT";
    public static final String INSERT_DENIED = "INSERT_DENIED";
    public static final String OPERATION_DENIED = "OPERATION_DENIED";
    public static final String DELETION_DENIED = "DELETION_DENIED";
    public static final String ACTION_DENIED = "ACTION_DENIED";
}