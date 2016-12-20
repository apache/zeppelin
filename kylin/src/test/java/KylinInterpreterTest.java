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

import org.apache.http.*;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.kylin.KylinInterpreter;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class KylinInterpreterTest {
  static final Properties kylinProperties = new Properties();

  @BeforeClass
  public static void setUpClass() {
    kylinProperties.put("kylin.api.url", "http://localhost:7070/kylin/api/query");
    kylinProperties.put("kylin.api.user", "ADMIN");
    kylinProperties.put("kylin.api.password", "KYLIN");
    kylinProperties.put("kylin.query.project", "default");
    kylinProperties.put("kylin.query.offset", "0");
    kylinProperties.put("kylin.query.limit", "5000");
    kylinProperties.put("kylin.query.ispartial", "true");
  }

  @Test
  public void testWithDefault(){
    KylinInterpreter t = new MockKylinInterpreter(getDefaultProperties());
    InterpreterResult result = t.interpret(
        "select a.date,sum(b.measure) as measure from kylin_fact_table a " +
            "inner join kylin_lookup_table b on a.date=b.date group by a.date", null);
    assertEquals("default", t.getProject("select a.date,sum(b.measure) as measure " +
                    "from kylin_fact_table a inner join kylin_lookup_table b on a.date=b.date group by a.date"));
    assertEquals(InterpreterResult.Type.TABLE,result.message().get(0).getType());
  }

  @Test
  public void testWithProject(){
    KylinInterpreter t = new MockKylinInterpreter(getDefaultProperties());
    assertEquals("project2", t.getProject("(project2)\n select a.date,sum(b.measure) as measure " +
            "from kylin_fact_table a inner join kylin_lookup_table b on a.date=b.date group by a.date"));
    assertEquals("", t.getProject("()\n select a.date,sum(b.measure) as measure " +
            "from kylin_fact_table a inner join kylin_lookup_table b on a.date=b.date group by a.date"));
  }

  private Properties getDefaultProperties(){
    Properties prop = new Properties();
    prop.put("kylin.api.username", "ADMIN");
    prop.put("kylin.api.password", "KYLIN");
    prop.put("kylin.api.url", "http://<host>:<port>/kylin/api/query");
    prop.put("kylin.query.project", "default");
    prop.put("kylin.query.offset", "0");
    prop.put("kylin.query.limit", "5000");
    prop.put("kylin.query.ispartial", "true");
    return prop;
  }
}

class MockKylinInterpreter extends KylinInterpreter {

  public MockKylinInterpreter(Properties property) {
    super(property);
  }

  @Override
  public HttpResponse prepareRequest(String sql) throws IOException {
    MockHttpClient client = new MockHttpClient();
    return client.execute(new HttpPost());
  }

}

class MockHttpClient{
  public MockHttpResponse execute(HttpPost post){
    return new MockHttpResponse();
  }
}

class MockHttpResponse extends AbstractHttpMessage implements HttpResponse{

  @Override
  public StatusLine getStatusLine() {
    return new MockStatusLine();
  }

  @Override
  public void setStatusLine(StatusLine statusLine) {

  }

  @Override
  public void setStatusLine(ProtocolVersion protocolVersion, int i) {

  }

  @Override
  public void setStatusLine(ProtocolVersion protocolVersion, int i, String s) {

  }

  @Override
  public void setStatusCode(int i) throws IllegalStateException {

  }

  @Override
  public void setReasonPhrase(String s) throws IllegalStateException {

  }

  @Override
  public HttpEntity getEntity() {
    return new MockEntity();
  }

  @Override
  public void setEntity(HttpEntity httpEntity) {

  }

  @Override
  public Locale getLocale() {
    return null;
  }

  @Override
  public void setLocale(Locale locale) {

  }

  @Override
  public ProtocolVersion getProtocolVersion() {
    return null;
  }
}

class MockStatusLine implements StatusLine{

  @Override
  public ProtocolVersion getProtocolVersion() {
    return null;
  }

  @Override
  public int getStatusCode() {
    return 200;
  }

  @Override
  public String getReasonPhrase() {
    return null;
  }
}

class MockEntity implements HttpEntity{

  @Override
  public boolean isRepeatable() {
    return false;
  }

  @Override
  public boolean isChunked() {
    return false;
  }

  @Override
  public long getContentLength() {
    return 0;
  }

  @Override
  public Header getContentType() {
    return null;
  }

  @Override
  public Header getContentEncoding() {
    return null;
  }

  @Override
  public InputStream getContent() throws IOException, IllegalStateException {
    return new ByteArrayInputStream(("{\"columnMetas\":" +
        "[{\"label\":\"PART_DT\"},{\"label\":\"measure\"}]," +
        "\"results\":[[\"2012-01-03\",\"917.4138\"]," +
        "[\"2012-05-06\",\"592.4823\"]]}").getBytes());
  }

  @Override
  public void writeTo(OutputStream outputStream) throws IOException {

  }

  @Override
  public boolean isStreaming() {
    return false;
  }

  @Override
  public void consumeContent() throws IOException {

  }
}
