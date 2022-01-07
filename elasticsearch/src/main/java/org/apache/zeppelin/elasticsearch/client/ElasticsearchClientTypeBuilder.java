package org.apache.zeppelin.elasticsearch.client;

import java.util.Arrays;

public class ElasticsearchClientTypeBuilder {

    public static Build withValue(String value){
        return new Builder(value);
    }

    public interface Build{
        ElasticsearchClientType build();
    }

    public static class Builder implements Build {
        private final String value;

        public Builder(String value) {
            this.value = value;
        }

        @Override
        public ElasticsearchClientType build() {
            boolean isExistingValue = Arrays.stream(ElasticsearchClientType.values()).anyMatch(clientType -> clientType.toString().equalsIgnoreCase(value));
            return isExistingValue ? ElasticsearchClientType.valueOf(value.toLowerCase()) : ElasticsearchClientType.UNKNOWN;
        }
    }
}
