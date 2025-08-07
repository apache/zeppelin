package org.apache.zeppelin.jdbc;

public class ValidationRequest {
    private String queryText;
    private String user;
    private String interpreterName;

    public ValidationRequest(String queryText, String user, String interpreterName) {
        this.queryText = queryText;
        this.user = user;
        this.interpreterName = interpreterName;
    }

    public String toJson() {
        return "{\"query_text\":\"" + queryText + "\",\"user\":\"" + user + "\",\"interpreter_name\":\"" + interpreterName + "\"}";
    }
}

