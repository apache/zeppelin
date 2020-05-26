/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.zeppelin.influxdb;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import javax.annotation.Nonnull;

import com.influxdb.LogLevel;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;


public class InfluxDBInterpeterTest {

  Properties properties;

  static final String SINGLE_TABLE_RESPONSE =
      "#datatype,string,long,dateTime:RFC3339,double,string\n" +
          "#group,false,false,false,false,true\n" +
          "#default,_result,,,,\n" +
          ",result,table,_time,_value,_field\n" +
          ",,0,2020-01-24T10:23:56Z,12.114014251781473,usage_user\n" +
          ",,0,2020-01-24T10:23:57Z,12.048493938257717,usage_user\n" +
          ",,0,2020-01-24T10:24:06Z,12.715678919729932,usage_user\n" +
          ",,0,2020-01-24T10:24:07Z,11.876484560570072,usage_user\n" +
          ",,0,2020-01-24T10:24:16Z,10.044977511244378,usage_user\n" +
          ",,0,2020-01-24T10:24:17Z,10.594702648675662,usage_user\n" +
          ",,0,2020-01-24T10:24:26Z,12.092034512942353,usage_user\n" +
          ",,0,2020-01-24T10:24:27Z,12.131065532766383,usage_user\n" +
          ",,0,2020-01-24T10:24:36Z,14.332125452955141,usage_user\n" +
          ",,0,2020-01-24T10:24:37Z,15.153788447111777,usage_user";

  static final String MULTI_TABLE_RESPONSE =
      "#datatype,string,long,dateTime:RFC3339,dateTime:RFC3339," +
          "string,string,string,string,double,dateTime:RFC3339\n" +
          "#group,false,false,true,true,true,true,true,true,false,false\n" +
          "#default,_result,,,,,,,,,\n" +
          ",result,table,_start,_stop,_field,_measurement,cpu,host,_value,_time\n" +
          ",,0,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu," +
          "cpu-total,macek.local,12.381414297598637,2020-01-24T09:28:00Z\n" +
          ",,0,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu," +
          "cpu-total,macek.local,18.870254041431455,2020-01-24T09:29:00Z\n" +
          ",,0,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu," +
          "cpu-total,macek.local,26.64080311971415,2020-01-24T09:30:00Z\n" +
          ",,0,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu," +
          "cpu-total,macek.local,11.644120979499911,2020-01-24T09:31:00Z\n" +
          ",,0,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu," +
          "cpu-total,macek.local,16.046354351571846,2020-01-24T09:32:00Z\n" +
          ",,1,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu0," +
          "macek.local,23.525686625686625,2020-01-24T09:28:00Z\n" +
          ",,1,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu0," +
          "macek.local,31.582258129037516,2020-01-24T09:29:00Z\n" +
          ",,1,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu0," +
          "macek.local,39.20349852756812,2020-01-24T09:30:00Z\n" +
          ",,1,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu0," +
          "macek.local,23.533275499942164,2020-01-24T09:31:00Z\n" +
          ",,1,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu0," +
          "macek.local,19.11247206247206,2020-01-24T09:32:00Z\n" +
          ",,2,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu1," +
          "macek.local,3.775801800801801,2020-01-24T09:28:00Z\n" +
          ",,2,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu1," +
          "macek.local,8.776226876226875,2020-01-24T09:29:00Z\n" +
          ",,2,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu1," +
          "macek.local,16.15592568092568,2020-01-24T09:30:00Z\n" +
          ",,2,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu1," +
          "macek.local,3.466367149700483,2020-01-24T09:31:00Z\n" +
          ",,2,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu1," +
          "macek.local,10.123511023511023,2020-01-24T09:32:00Z\n" +
          ",,3,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu2," +
          "macek.local,23.186861861861857,2020-01-24T09:28:00Z\n" +
          ",,3,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu2," +
          "macek.local,30.502449226101927,2020-01-24T09:29:00Z\n" +
          ",,3,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu2," +
          "macek.local,37.800263500263505,2020-01-24T09:30:00Z\n" +
          ",,3,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu2," +
          "macek.local,21.04487655320989,2020-01-24T09:31:00Z\n" +
          ",,3,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu2," +
          "macek.local,23.40988960155627,2020-01-24T09:32:00Z\n" +
          ",,4,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu3," +
          "macek.local,3.7013513513513514,2020-01-24T09:28:00Z\n" +
          ",,4,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu3," +
          "macek.local,8.669684156858507,2020-01-24T09:29:00Z\n" +
          ",,4,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu3," +
          "macek.local,16.4761093606771,2020-01-24T09:30:00Z\n" +
          ",,4,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu3," +
          "macek.local,3.416193908762379,2020-01-24T09:31:00Z\n" +
          ",,4,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu3," +
          "macek.local,10.391479708146376,2020-01-24T09:32:00Z\n" +
          ",,5,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu4," +
          "macek.local,20.520504495504497,2020-01-24T09:28:00Z\n" +
          ",,5,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu4," +
          "macek.local,28.435828535828534,2020-01-24T09:29:00Z\n" +
          ",,5,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu4," +
          "macek.local,35.76454396684968,2020-01-24T09:30:00Z\n" +
          ",,5,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu4," +
          "macek.local,18.94977031643698,2020-01-24T09:31:00Z\n" +
          ",,5,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu4," +
          "macek.local,22.81423008923009,2020-01-24T09:32:00Z\n" +
          ",,6,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu5," +
          "macek.local,3.4502771752771753,2020-01-24T09:28:00Z\n" +
          ",,6,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu5," +
          "macek.local,8.617365310885685,2020-01-24T09:29:00Z\n" +
          ",,6,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu5," +
          "macek.local,16.5813353653174,2020-01-24T09:30:00Z\n" +
          ",,6,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu5," +
          "macek.local,3.341634649967983,2020-01-24T09:31:00Z\n" +
          ",,6,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu5," +
          "macek.local,10.489286880953548,2020-01-24T09:32:00Z\n" +
          ",,7,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu6," +
          "macek.local,17.42073857073857,2020-01-24T09:28:00Z\n" +
          ",,7,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu6," +
          "macek.local,25.555054526024517,2020-01-24T09:29:00Z\n" +
          ",,7,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu6," +
          "macek.local,34.19774496441163,2020-01-24T09:30:00Z\n" +
          ",,7,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu6," +
          "macek.local,15.985298393631725,2020-01-24T09:31:00Z\n" +
          ",,7,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu6," +
          "macek.local,21.359203467536798,2020-01-24T09:32:00Z\n" +
          ",,8,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu7," +
          "macek.local,3.4507517507517504,2020-01-24T09:28:00Z\n" +
          ",,8,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu7," +
          "macek.local,8.817554700888033,2020-01-24T09:29:00Z\n" +
          ",,8,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu7," +
          "macek.local,16.957243048909714,2020-01-24T09:30:00Z\n" +
          ",,8,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu7," +
          "macek.local,3.408601950268617,2020-01-24T09:31:00Z\n" +
          ",,8,2020-01-24T09:27:44.8452185Z,2020-01-24T10:27:44.8452185Z,usage_user,cpu,cpu7," +
          "macek.local,10.672760839427506,2020-01-24T09:32:00Z";

  protected MockWebServer mockServer;

  /**
   * Start Mock server.
   *
   * @return the mock server URL
   */
  @Nonnull
  protected String startMockServer() {

    mockServer = new MockWebServer();
    try {
      mockServer.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return mockServer.url("/").url().toString();
  }


  @Nonnull
  protected MockResponse createResponse(final String data) {
    return createResponse(data, "text/csv", true);
  }

  @Nonnull
  protected MockResponse createResponse(final String data, final String
      contentType, final boolean chunked) {

    MockResponse response = new MockResponse()
        .setHeader("Content-Type", contentType + "; charset=utf-8")
        .setHeader("Date", "Tue, 26 Jun 2018 13:15:01 GMT");

    if (chunked) {
      response.setChunkedBody(data, data.length());
    } else {
      response.setBody(data);
    }

    return response;
  }


  @Before
  public void before() throws InterpreterException {
    //properties for local influxdb2 server
    properties = new Properties();
    //properties.setProperty("influxdb.url", "http://localhost:9999");
    properties.setProperty("influxdb.url", startMockServer());

    properties.setProperty("influxdb.token", "my-token");
    properties.setProperty("influxdb.org", "my-org");
    properties.setProperty("influxdb.logLevel", LogLevel.BODY.toString());
  }

  @After
  public void after() throws IOException {
    if (mockServer != null) {
      mockServer.shutdown();
    }
  }

  @Test
  public void testSigleTable() throws InterpreterException {

    InfluxDBInterpreter t = new InfluxDBInterpreter(properties);
    t.open();

    //just for testing with real influxdb (not used in mock)
    String flux = "from(bucket: \"my-bucket\")\n" +
        "  |> range(start:-1m)\n" +
        "  |> filter(fn: (r) => r._measurement == \"cpu\")\n" +
        "  |> filter(fn: (r) => r._field == \"usage_user\")\n" +
        "  |> filter(fn: (r) => r.cpu == \"cpu-total\")\n" +
        "  |> limit(n:5, offset: 0)" +
        "  |> keep(columns: [\"_field\", \"_value\", \"_time\"])";

    InterpreterContext context = InterpreterContext.builder()
        .setAuthenticationInfo(new AuthenticationInfo("testUser"))
        .build();

    mockServer.enqueue(createResponse(SINGLE_TABLE_RESPONSE));

    InterpreterResult interpreterResult = t.interpret(flux, context);

    // if prefix not found return ERROR and Prefix not found.
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());

    List<InterpreterResultMessage> message = interpreterResult.message();
    Assert.assertEquals(1, message.size());
    Assert.assertEquals(InterpreterResult.Type.TABLE, message.get(0).getType());
    Assert.assertEquals("result\ttable\t_time\t_value\t_field\n" +
            "_result\t0\t2020-01-24T10:23:56Z\t12.114014251781473\tusage_user\n" +
            "_result\t0\t2020-01-24T10:23:57Z\t12.048493938257717\tusage_user\n" +
            "_result\t0\t2020-01-24T10:24:06Z\t12.715678919729932\tusage_user\n" +
            "_result\t0\t2020-01-24T10:24:07Z\t11.876484560570072\tusage_user\n" +
            "_result\t0\t2020-01-24T10:24:16Z\t10.044977511244378\tusage_user\n" +
            "_result\t0\t2020-01-24T10:24:17Z\t10.594702648675662\tusage_user\n" +
            "_result\t0\t2020-01-24T10:24:26Z\t12.092034512942353\tusage_user\n" +
            "_result\t0\t2020-01-24T10:24:27Z\t12.131065532766383\tusage_user\n" +
            "_result\t0\t2020-01-24T10:24:36Z\t14.332125452955141\tusage_user\n" +
            "_result\t0\t2020-01-24T10:24:37Z\t15.153788447111777\tusage_user\n",
        message.get(0).getData());

    t.close();
  }

  @Test
  public void testMultiTable() throws InterpreterException {

    InfluxDBInterpreter t = new InfluxDBInterpreter(properties);
    t.open();

    //just for testing with real influxdb (not used in mock)
    String flux = "from(bucket: \"my-bucket\")\n" +
        "  |> range(start: -1h)\n" +
        "  |> filter(fn: (r) => r._measurement == \"cpu\")\n" +
        "  |> filter(fn: (r) => r._field == \"usage_user\")\n" +
        "  |> aggregateWindow(every: 1m, fn: mean)\n" +
        "  |> limit(n:5, offset: 0)";

    InterpreterContext context = InterpreterContext.builder()
        .setAuthenticationInfo(new AuthenticationInfo("testUser"))
        .build();

    mockServer.enqueue(createResponse(MULTI_TABLE_RESPONSE));
    InterpreterResult interpreterResult = t.interpret(flux, context);

    // if prefix not found return ERROR and Prefix not found.
    if (InterpreterResult.Code.ERROR.equals(interpreterResult.code())) {
      Assert.fail(interpreterResult.toString());
    }

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    List<InterpreterResultMessage> message = interpreterResult.message();

    Assert.assertEquals(9, message.size());

    message.forEach(m -> Assert.assertEquals(InterpreterResult.Type.TABLE, m.getType()));

    Assert.assertEquals(
        "result\ttable\t_start\t_stop\t_field\t_measurement\tcpu\thost\t_value\t_time\n" +
        "_result\t0\t2020-01-24T09:27:44.845218500Z\t2020-01-24T10:27:44.845218500Z\tusage_user" +
        "\tcpu\tcpu-total\tmacek.local\t12.381414297598637\t2020-01-24T09:28:00Z\n" +
        "_result\t0\t2020-01-24T09:27:44.845218500Z\t2020-01-24T10:27:44.845218500Z\tusage_user" +
            "\tcpu\tcpu-total\tmacek.local\t18.870254041431455\t2020-01-24T09:29:00Z\n" +
        "_result\t0\t2020-01-24T09:27:44.845218500Z\t2020-01-24T10:27:44.845218500Z\tusage_user" +
            "\tcpu\tcpu-total\tmacek.local\t26.64080311971415\t2020-01-24T09:30:00Z\n" +
        "_result\t0\t2020-01-24T09:27:44.845218500Z\t2020-01-24T10:27:44.845218500Z\tusage_user" +
            "\tcpu\tcpu-total\tmacek.local\t11.644120979499911\t2020-01-24T09:31:00Z\n" +
        "_result\t0\t2020-01-24T09:27:44.845218500Z\t2020-01-24T10:27:44.845218500Z\tusage_user" +
            "\tcpu\tcpu-total\tmacek.local\t16.046354351571846\t2020-01-24T09:32:00Z\n",
        message.get(0).getData());

    Assert.assertEquals("result\ttable\t_start\t_stop\t_field\t_measurement\tcpu\thost\t_value" +
        "\t_time\n" +
        "_result\t8\t2020-01-24T09:27:44.845218500Z\t2020-01-24T10:27:44.845218500Z\tusage_user" +
        "\tcpu\tcpu7\tmacek.local\t3.4507517507517504\t2020-01-24T09:28:00Z\n" +
        "_result\t8\t2020-01-24T09:27:44.845218500Z\t2020-01-24T10:27:44.845218500Z\tusage_user" +
        "\tcpu\tcpu7\tmacek.local\t8.817554700888033\t2020-01-24T09:29:00Z\n" +
        "_result\t8\t2020-01-24T09:27:44.845218500Z\t2020-01-24T10:27:44.845218500Z\tusage_user" +
        "\tcpu\tcpu7\tmacek.local\t16.957243048909714\t2020-01-24T09:30:00Z\n" +
        "_result\t8\t2020-01-24T09:27:44.845218500Z\t2020-01-24T10:27:44.845218500Z\tusage_user" +
        "\tcpu\tcpu7\tmacek.local\t3.408601950268617\t2020-01-24T09:31:00Z\n" +
        "_result\t8\t2020-01-24T09:27:44.845218500Z\t2020-01-24T10:27:44.845218500Z\tusage_user" +
        "\tcpu\tcpu7\tmacek.local\t10.672760839427506\t2020-01-24T09:32:00Z\n",
        message.get(8).getData());

    t.close();
  }

}
