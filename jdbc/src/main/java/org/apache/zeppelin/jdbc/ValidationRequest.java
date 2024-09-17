package org.apache.zeppelin.jdbc;

public class ValidationRequest {
    private String queryText;

    public ValidationRequest(String queryText) {
        this.queryText = queryText;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public String toJson() {
        return "{\"query_text\":\"" + queryText + "\"}";
    }
}

