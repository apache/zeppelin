/*
 * Copyright 2011-2012 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.lens;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
 
/**
 * workaround for https://github.com/spring-projects/spring-shell/issues/73.
 */
public class LensBootstrap extends org.springframework.shell.Bootstrap {
  public LensBootstrap() {
    super();
  }
  public LensJLineShellComponent getLensJLineShellComponent() {
    GenericApplicationContext ctx = (GenericApplicationContext) getApplicationContext();
    RootBeanDefinition rbd = new RootBeanDefinition();
    rbd.setBeanClass(LensJLineShellComponent.class);
    DefaultListableBeanFactory bf = (DefaultListableBeanFactory) ctx.getBeanFactory();
    bf.registerBeanDefinition(LensJLineShellComponent.class.getSimpleName(), rbd);
    return ctx.getBean(LensJLineShellComponent.class);
  }
}
