package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.LogService;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.job.JobLogger;

/**
 * Generic logger to support LogService and JobLogger.  This is helpful when the code that
 * needs to log messages is not aware of the context in which it is executing (Job vs. Trigger/Action, etc.).
 */

@UserDefinedClassInfo
public class Logger {

    private JobLogger jobLogger;
    private LogService logService;

    public Logger(JobLogger jobLogger) {
      this.jobLogger = jobLogger;
      this.logService = ServiceLocator.locate(LogService.class);
    }

    public Logger() {
      this.jobLogger = null;
      this.logService = ServiceLocator.locate(LogService.class);
    }

    public void info(String message) {
      if (this.jobLogger != null) {
        this.jobLogger.log(message);
      }
      this.logService.info(message);
    }

    public void error(String message) {
       if (this.jobLogger != null) {
         this.jobLogger.log(message);
       }
       this.logService.error(message);
    }

    public void debug(String message) {
      if (this.jobLogger != null) {
        this.jobLogger.log(message);
      }
      this.logService.debug(message);
    }

}