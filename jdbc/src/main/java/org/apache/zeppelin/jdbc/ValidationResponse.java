package org.apache.zeppelin.jdbc;

public class ValidationResponse {
    private boolean preSubmitFail;
    private boolean failFast;
    private boolean failedByDeprecatedTable;
    private String message;

    // Getters and Setters
    public boolean isPreSubmitFail() {
        return preSubmitFail;
    }

    public void setPreSubmitFail(boolean preSubmitFail) {
        this.preSubmitFail = preSubmitFail;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public boolean isFailedByDeprecatedTable() {
        return failedByDeprecatedTable;
    }

    public void setFailedByDeprecatedTable(boolean failedByDeprecatedTable) {
        this.failedByDeprecatedTable = failedByDeprecatedTable;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static ValidationResponse fromJson(String jsonResponse) {
        ValidationResponse response = new ValidationResponse();
        // Use simple JSON parsing (can replace with a library like Jackson or Gson)
        response.setPreSubmitFail(jsonResponse.contains("\"pre_submit_fail\":true"));
        response.setFailFast(jsonResponse.contains("\"fail_fast\":true"));
        response.setFailedByDeprecatedTable(jsonResponse.contains("\"failed_by_deprecated_table\":true"));

        int messageIndex = jsonResponse.indexOf("\"message\":\"");
        if (messageIndex != -1) {
            int messageEnd = jsonResponse.indexOf("\"", messageIndex + 10);
            String message = jsonResponse.substring(messageIndex + 10, messageEnd);
            response.setMessage(message);
        }
        return response;
    }
}
