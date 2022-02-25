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


package org.apache.zeppelin.flink;

import java.util.function.Consumer;

public class FlinkSqlContext {

    private Object benv;
    private Object senv;
    private Object btenv;
    private Object stenv;
    private Object z;
    private Consumer<String> streamSqlSelectConsumer;

    public FlinkSqlContext(Object benv,
                           Object senv,
                           Object btenv,
                           Object stenv,
                           Object z,
                           Consumer<String> streamSqlSelectConsumer) {
        this.benv = benv;
        this.senv = senv;
        this.btenv = btenv;
        this.stenv = stenv;
        this.z = z;
        this.streamSqlSelectConsumer = streamSqlSelectConsumer;
    }

    public Object getBenv() {
        return benv;
    }

    public Object getSenv() {
        return senv;
    }

    public Object getBtenv() {
        return btenv;
    }

    public Object getStenv() {
        return stenv;
    }

    public Object getZeppelinContext() {
        return z;
    }

    public Consumer<String> getStreamSqlSelectConsumer() {
        return streamSqlSelectConsumer;
    }
}
