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

import junit.framework.Assert;
import org.apache.commons.configuration.ConfigurationException;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;


/**
 * Created by joelz on 8/19/15.
 */
public class ZeppelinConfigurationTest {
    @Test
    public void getAllowedOrigins2Test() throws MalformedURLException, ConfigurationException {

        ZeppelinConfiguration conf  = new ZeppelinConfiguration(this.getClass().getResource("/test-zeppelin-site2.xml"));
        List<String> origins = conf.getAllowedOrigins();
        Assert.assertEquals(2, origins.size());
        Assert.assertEquals("http://onehost:8080", origins.get(0));
        Assert.assertEquals("http://otherhost.com", origins.get(1));
    }

    @Test
    public void getAllowedOrigins1Test() throws MalformedURLException, ConfigurationException {

        ZeppelinConfiguration conf  = new ZeppelinConfiguration(this.getClass().getResource("/test-zeppelin-site1.xml"));
        List<String> origins = conf.getAllowedOrigins();
        Assert.assertEquals(1, origins.size());
        Assert.assertEquals("http://onehost:8080", origins.get(0));
    }

    @Test
    public void getAllowedOriginsNoneTest() throws MalformedURLException, ConfigurationException {

        ZeppelinConfiguration conf  = new ZeppelinConfiguration(this.getClass().getResource("/zeppelin-site.xml"));
        List<String> origins = conf.getAllowedOrigins();
        Assert.assertEquals(1, origins.size());
    }

    @Test
    public void isWindowsPathTestTrue() throws ConfigurationException {

        ZeppelinConfiguration conf  = new ZeppelinConfiguration(this.getClass().getResource("/zeppelin-site.xml"));
        Boolean isIt = conf.isWindowsPath("c:\\test\\file.txt");
        Assert.assertTrue(isIt);
    }

    @Test
    public void isWindowsPathTestFalse() throws ConfigurationException {

        ZeppelinConfiguration conf  = new ZeppelinConfiguration(this.getClass().getResource("/zeppelin-site.xml"));
        Boolean isIt = conf.isWindowsPath("~/test/file.xml");
        Assert.assertFalse(isIt);
    }
}
