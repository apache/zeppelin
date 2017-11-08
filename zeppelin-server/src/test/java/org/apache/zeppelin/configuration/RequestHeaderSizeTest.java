package org.apache.zeppelin.configuration;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class RequestHeaderSizeTest extends AbstractTestRestApi {
    private static final Logger LOG = LoggerFactory.getLogger(RequestHeaderSizeTest.class);
    private static final int REQUEST_HEADER_MAX_SIZE = 20000;

    @Before
    public void startZeppelin() throws Exception {
        System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_JETTY_REQUEST_HEADER_SIZE.getVarName(), String.valueOf(REQUEST_HEADER_MAX_SIZE));
        startUp(RequestHeaderSizeTest.class.getSimpleName());
    }

    @After
    public void stopZeppelin() throws Exception {
        shutDown();
    }


    @Test
    public void increased_request_header_size_do_not_cause_413_when_request_size_is_over_8K() throws Exception {
        LOG.info("starting test 'increased_request_header_size_do_not_cause_413_when_request_size_is_over_8K'");
        HttpClient httpClient = new HttpClient();

        GetMethod getMethod = new GetMethod(getUrlToTest() + "/version");
        String headerValue = RandomStringUtils.randomAlphanumeric(REQUEST_HEADER_MAX_SIZE - 2000);
        getMethod.setRequestHeader("too_large_header", headerValue);
        LOG.info("length is:" + getMethod.toString().length());
        int httpCode = httpClient.executeMethod(getMethod);
        assertThat(httpCode, is(HttpStatus.SC_OK));


        getMethod = new GetMethod(getUrlToTest() + "/version");
        headerValue = RandomStringUtils.randomAlphanumeric(REQUEST_HEADER_MAX_SIZE+2000);
        LOG.info("length of header value is:" + headerValue.length());
        LOG.info("header value is:" + headerValue);

        getMethod.setRequestHeader("too_large_header", headerValue);
        LOG.info("headers are:" + Arrays.toString(getMethod.getRequestHeaders().length()));

        httpCode = httpClient.executeMethod(getMethod);
        assertThat(httpCode, is(HttpStatus.SC_REQUEST_TOO_LONG));
    }


}
