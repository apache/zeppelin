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

package org.apache.zeppelin.display;

import org.apache.zeppelin.display.ui.OptionInput.ParamOption;

/**
 * Old Input type.
 * The reason I still keep Old Input is for compatibility. There's one bug in the old input forms.
 * There's 2 ways to create input forms: frontend & backend.
 * The bug is in frontend. The type would not be set correctly when input form
 * is created in frontend (Input.getInputForm).
 */
public class OldInput extends Input<Object> {

  ParamOption[] options;

  public OldInput() {}

  public OldInput(String name, Object defaultValue) {
    this.name = name;
    this.displayName = name;
    this.defaultValue = defaultValue;
  }

  public OldInput(String name, Object defaultValue, ParamOption[] options) {
    this.name = name;
    this.displayName = name;
    this.defaultValue = defaultValue;
    this.options = options;
  }

  @Override
  public boolean equals(Object o) {
    return name.equals(((OldInput) o).getName());
  }

  public ParamOption[] getOptions() {
    return options;
  }

  public void setOptions(ParamOption[] options) {
    this.options = options;
  }

  /**
   *
   */
  public static class OldTextBox extends OldInput {
    public OldTextBox(String name, Object defaultValue) {
      super(name, defaultValue);
    }
  }

  /**
   *
   */
  public static class OldSelect extends OldInput {
    public OldSelect(String name, Object defaultValue, ParamOption[] options) {
      super(name, defaultValue, options);
    }
  }

  /**
   *
   */
  public static class OldCheckBox extends OldInput {
    public OldCheckBox(String name, Object defaultValue, ParamOption[] options) {
      super(name, defaultValue, options);
    }
  }
}
