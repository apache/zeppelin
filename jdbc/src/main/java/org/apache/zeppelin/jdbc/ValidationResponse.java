package org.apache.zeppelin.jdbc;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
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

        JsonElement jsonElement = gson.fromJson(jsonResponse, JsonElement.class);

        if (jsonElement.isJsonObject()) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            if (jsonObject.has("pre_submit_fail") && !jsonObject.get("pre_submit_fail").isJsonNull()) {
                response.setPreSubmitFail(jsonObject.get("pre_submit_fail").getAsBoolean());
            }
            if (jsonObject.has("fail_fast") && !jsonObject.get("fail_fast").isJsonNull()) {
                response.setFailFast(jsonObject.get("fail_fast").getAsBoolean());
            }
            if (jsonObject.has("failed_by_deprecated_table") && !jsonObject.get("failed_by_deprecated_table").isJsonNull()) {
                response.setFailedByDeprecatedTable(jsonObject.get("failed_by_deprecated_table").getAsBoolean());
            }
            if (jsonObject.has("message") && !jsonObject.get("message").isJsonNull()) {
                response.setMessage(jsonObject.get("message").getAsString());
            }
        } else {
            response.setPreSubmitFail(false);
            response.setFailFast(false);
            response.setFailedByDeprecatedTable(false);
            response.setMessage(""); // Default message
        }
        return response;
    }
}
