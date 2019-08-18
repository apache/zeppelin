package org.apache.zeppelin.ksql;

import java.util.List;

public class TopicInfo {
  private String name = "";
  private boolean registered = false;
  private int partitionCount = 0;
  private List<Integer> replicaInfo = null;
  private int consumerCount = 0;
  private int consumerGroupCount = 0;

  public TopicInfo() {
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isRegistered() {
    return registered;
  }

  public void setRegistered(boolean registered) {
    this.registered = registered;
  }

  public int getPartitionCount() {
    return partitionCount;
  }

  public void setPartitionCount(int partitionCount) {
    this.partitionCount = partitionCount;
  }

  public List<Integer> getReplicaInfo() {
    return replicaInfo;
  }

  public void setReplicaInfo(List<Integer> replicaInfo) {
    this.replicaInfo = replicaInfo;
  }

  public int getConsumerCount() {
    return consumerCount;
  }

  public void setConsumerCount(int consumerCount) {
    this.consumerCount = consumerCount;
  }

  public int getConsumerGroupCount() {
    return consumerGroupCount;
  }

  public void setConsumerGroupCount(int consumerGroupCount) {
    this.consumerGroupCount = consumerGroupCount;
  }
}
