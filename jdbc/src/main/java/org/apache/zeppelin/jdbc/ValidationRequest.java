package org.apache.zeppelin.jdbc;

public class ValidationRequest {
    private String queryText;
    private String user;

    public ValidationRequest(String queryText, String user) {
        this.queryText = queryText;
        this.user = user;
    }

    public String toJson() {
        return "{\"queryText\":\"" + queryText + "\",\"user\":\"" + user + "\"}";
    }
}

