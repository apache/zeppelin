/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.jupyter.nbformat;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 *
 */
public class Metadata {

  @SerializedName("kernelspec")
  private Kernelspec kernelspec;

  @SerializedName("language_info")
  private LanguageInfo languageInfo;

  @SerializedName("orig_nbformat")
  private int origNbformat;

  @SerializedName("title")
  private String title;

  @SerializedName("authors")
  private List<Author> authors;

  public Kernelspec getKernelspec() {
    return kernelspec;
  }

  public LanguageInfo getLanguageInfo() {
    return languageInfo;
  }

  public int getOrigNbformat() {
    return origNbformat;
  }

  public String getTitle() {
    return title;
  }

  public List<Author> getAuthors() {
    return authors;
  }
}
