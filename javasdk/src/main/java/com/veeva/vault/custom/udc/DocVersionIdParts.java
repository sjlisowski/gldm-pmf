package com.veeva.vault.custom.udc;
/*
  A simple class to split document version ID's in string format into the various components
  of the version id.  The constructor assumes the input value is in the format:
  "<docid>_<major>_<minor>", e.g. "10325_1_0".

  This class assumes the String provided in the constructor is in the form of a Vault
  document version ID.  No checking is done on the argument.
*/

import com.veeva.vault.sdk.api.core.StringUtils;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;

@UserDefinedClassInfo
public class DocVersionIdParts {
    public String id;
    public int major;
    public int minor;

    /**
     *
     * @param docVersionId in the form "id_major_minor", e.g. "26_0_1".
     */
    public DocVersionIdParts(String docVersionId) {
        String a[] = StringUtils.split(docVersionId, "_");
        this.id = a[0];
        this.major = Integer.parseInt(a[1]);
        this.minor = Integer.parseInt(a[2]);
    }

    public static String id(String docVersionId) { return (new DocVersionIdParts(docVersionId).id); }

    public static int major(String docVersionId) {
        return (new DocVersionIdParts(docVersionId).major);
    }

    public static int minor(String docVersionId) {
        return (new DocVersionIdParts(docVersionId).minor);
    }
}