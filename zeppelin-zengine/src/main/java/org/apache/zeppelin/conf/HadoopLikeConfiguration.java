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
package org.apache.zeppelin.conf;

import org.apache.commons.configuration.AbstractFileConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.io.SAXReader;

import java.io.Reader;
import java.io.Writer;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Configuration in hadoop like format:
 * <p><pre>
 * <configuration>
 *   <property>
 *     <name>zeppelin.server.addr</name>
 *     <value>0.0.0.0</value>
 *     <description>Server address</description>
 *   </property>
 *   <property>
 *     <name>zeppelin.server.port</name>
 *     <value>8080</value>
 *     <description>Server port.</description>
 *   </property>
 *   ...
 * </pre></p>
 */
public class HadoopLikeConfiguration extends AbstractFileConfiguration {

  @Override
  public void load(Reader in) throws ConfigurationException {
    try {
      SAXReader reader = new SAXReader();
      Document document = reader.read(in);
      Element root = document.getRootElement();
      checkArgument(root.getQName().equals(QName.get("configuration")));

      for (Element property : (List<Element>) root.elements(QName.get("property"))) {
        String name = property.element(QName.get("name")).getText();
        String value = property.element(QName.get("value")).getText();
        addProperty(name, value);
      }
    } catch (DocumentException e) {
      throw new ConfigurationException(e);
    }
  }

  @Override
  public void save(Writer out) throws ConfigurationException {
    throw new UnsupportedOperationException();
  }
}
