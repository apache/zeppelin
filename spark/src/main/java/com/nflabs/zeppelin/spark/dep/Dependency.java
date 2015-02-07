package com.nflabs.zeppelin.spark.dep;

import java.util.LinkedList;
import java.util.List;

/**
 *
 */
public class Dependency {
  private String groupArtifactVersion;
  private boolean dist = false;
  private boolean recursive = false;
  private List<String> exclusions;


  public Dependency(String groupArtifactVersion) {
    this.groupArtifactVersion = groupArtifactVersion;
    exclusions = new LinkedList<String>();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Dependency)) {
      return false;
    } else {
      return ((Dependency) o).groupArtifactVersion.equals(groupArtifactVersion);
    }
  }

  /**
   * Add artifact into SparkContext (sc.addJar())
   * @return
   */
  public Dependency dist() {
    dist = true;
    return this;
  }

  public Dependency recursive() {
    recursive = true;
    return this;
  }

  /**
   *
   * @param exclusions comma or newline separated list of "groupId:ArtifactId"
   * @return
   */
  public Dependency exclude(String exclusions) {
    recursive();
    for (String item : exclusions.split(",|\n")) {
      this.exclusions.add(item);
    }

    return this;
  }


  public String getGroupArtifactVersion() {
    return groupArtifactVersion;
  }

  public boolean isDist() {
    return dist;
  }

  public boolean isRecursive() {
    return recursive;
  }

  public List<String> getExclusions() {
    return exclusions;
  }

  public boolean isLocalFsArtifact() {
    int numSplits = groupArtifactVersion.split(":").length;
    return !(numSplits >= 3 && numSplits <= 5);
  }

}
