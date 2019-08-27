package org.apache.zeppelin.ksql;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

public class Response {
  @JsonProperty("@type")
  public String type = "";
  public String statementText = "";

  public Optional<TopicInfo[]> topics;
  public Optional<FunctionInfo[]> functions;

  // status/errors
  public Optional<CommandStatus> commandStatus;
  public Optional<String> message;
  public Optional<Long> error_code;
  public Optional<List<String>> stackTrace;

  // classes declarations
  public static class CommandStatus {
    public String status;
    public String message;
  }

  public static class TopicInfo {
    public String name = "";
    public boolean registered = false;
    public int partitionCount = 0;
    public List<Integer> replicaInfo = null;
    public int consumerCount = 0;
    public int consumerGroupCount = 0;
  }

  public static class FunctionInfo {
    public String name = "";
    public String type = "";
  }

}
