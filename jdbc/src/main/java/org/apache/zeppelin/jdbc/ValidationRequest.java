package org.apache.zeppelin.jdbc;

public class ValidationRequest {
    private String queryText;

    // Constructor
    public ValidationRequest(String queryText) {
        this.queryText = queryText;
    }

    // Getter and Setter
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

