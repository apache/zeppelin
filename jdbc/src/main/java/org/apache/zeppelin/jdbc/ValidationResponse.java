package org.apache.zeppelin.jdbc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
        Gson gson = new Gson();
        ValidationResponse response = new ValidationResponse();

        JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);

        response.setPreSubmitFail(jsonObject.get("pre_submit_fail").getAsBoolean());
        response.setFailFast(jsonObject.get("fail_fast").getAsBoolean());
        response.setFailedByDeprecatedTable(jsonObject.get("failed_by_deprecated_table").getAsBoolean());

        // Extract the "message" field
        if (jsonObject.has("message")) {
            response.setMessage(jsonObject.get("message").getAsString());
        }

        return response;
    }
}
