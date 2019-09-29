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

package org.apache.zeppelin.server;

import java.util.LinkedList;
import java.util.List;

import javax.inject.Singleton;

import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.utilities.ImmediateErrorHandler;

/**
 * ImmediateErrorHandlerImpl to catch exception
 */
@Singleton
public class ImmediateErrorHandlerImpl implements ImmediateErrorHandler {
        private final List<ErrorData> constructionErrors = new LinkedList<ErrorData>();
        private final List<ErrorData> destructionErrors = new LinkedList<ErrorData>();

        @Override
        public void postConstructFailed(ActiveDescriptor<?> immediateService,
                                                                                                                                        Throwable exception) {
                synchronized (this) {
                        constructionErrors.add(new ErrorData(immediateService, exception));
                        this.notifyAll();
                }
        }

        @Override
        public void preDestroyFailed(ActiveDescriptor<?> immediateService,
                                                                                                                         Throwable exception) {
                synchronized (this) {
                        destructionErrors.add(new ErrorData(immediateService, exception));
                        this.notifyAll();
                }
        }

         List<ErrorData> waitForAtLeastOneConstructionError(long waitTime) throws InterruptedException {
                synchronized (this) {
                        while (constructionErrors.size() <= 0 && waitTime > 0) {
                                long currentTime = System.currentTimeMillis();
                                wait(waitTime);
                                long elapsedTime = System.currentTimeMillis() - currentTime;
                                waitTime -= elapsedTime;
                        }
                        return new LinkedList<ErrorData>(constructionErrors);
                }
        }

}