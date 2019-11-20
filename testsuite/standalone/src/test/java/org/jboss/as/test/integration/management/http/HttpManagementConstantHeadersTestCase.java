/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.management.http;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.log4j.Logger;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.test.http.Authentication;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildflyTestRunner;

import static org.junit.Assert.*;

/**
 * Test case to test custom / constant headers are applied to existing contexts.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(WildflyTestRunner.class)
public class HttpManagementConstantHeadersTestCase {

    private static final int MGMT_PORT = 9990;
    private static final String MGMT_CTX = "/management";
    private static final String ERROR_CTX = "/error";

    private static final PathAddress INTERFACE_ADDRESS = PathAddress.pathAddress(PathElement.pathElement("core-service", "management"),
            PathElement.pathElement("management-interface", "http-interface"));

    @Inject
    protected ManagementClient managementClient;

    private URL managementUrl;
    private URL errorUrl;
    private HttpClient httpClient;

    @Before
    public void createClient() throws Exception {
        String address = managementClient.getMgmtAddress();
        this.managementUrl = new URL("http", address, MGMT_PORT, MGMT_CTX);
        this.errorUrl = new URL("http", address, MGMT_PORT, ERROR_CTX);

        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(managementUrl.getHost(), managementUrl.getPort()), new UsernamePasswordCredentials(Authentication.USERNAME, Authentication.PASSWORD));

        this.httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();
    }

    @After
    public void closeClient() {
        if (httpClient instanceof Closeable) {
            try {
                ((Closeable) httpClient).close();
            } catch (IOException e) {
                Logger.getLogger(XCorrelationIdTestCase.class).error("Failed closing client", e);
            }
        }
    }

    private void activateHeaders() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();
        headersMap.put("/", Collections.singletonList(Collections.singletonMap("X-All", "All")));
        headersMap.put("/management", Collections.singletonList(Collections.singletonMap("X-Management", "Management")));
        headersMap.put("/error", Collections.singletonList(Collections.singletonMap("X-Error", "Error")));

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));

        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
    }

    private static ModelNode createConstantHeadersOperation(final Map<String, List<Map<String, String>>> constantHeadersValues) {
        ModelNode writeAttribute = new ModelNode();
        writeAttribute.get("address").set(INTERFACE_ADDRESS.toModelNode());
        writeAttribute.get("operation").set("write-attribute");
        writeAttribute.get("name").set("constant-headers");

        ModelNode constantHeaders = new ModelNode();
        for (Entry<String, List<Map<String, String>>> entry : constantHeadersValues.entrySet()) {
            for (Map<String, String> header: entry.getValue()) {
                constantHeaders.add(createHeaderMapping(entry.getKey(), header));
            }
        }

        writeAttribute.get("value").set(constantHeaders);

        return writeAttribute;
    }

    private static ModelNode createHeaderMapping(final String path, final Map<String, String> headerValues) {
        ModelNode headerMapping = new ModelNode();
        headerMapping.get("path").set(path);
        ModelNode headers = new ModelNode();
        headers.add();     // Ensure the type of 'headers' is List even if no content is added.
        headers.remove(0);
        for (Entry<String, String> entry : headerValues.entrySet()) {
            ModelNode singleMapping = new ModelNode();
            singleMapping.get("name").set(entry.getKey());
            singleMapping.get("value").set(entry.getValue());
            headers.add(singleMapping);
        }
        headerMapping.get("headers").set(headers);

        return headerMapping;
    }

    @After
    public void removeHeaders() throws Exception {
        ModelNode undefineAttribute = new ModelNode();
        undefineAttribute.get("address").set(INTERFACE_ADDRESS.toModelNode());
        undefineAttribute.get("operation").set("undefine-attribute");
        undefineAttribute.get("name").set("constant-headers");

        managementClient.executeForResult(undefineAttribute);

        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
    }

    /**
     * Test that a call to the '/management' endpoint returns the expected headers.
     */
    @Test
    public void testManagement() throws Exception {
        activateHeaders();

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        Header header = response.getFirstHeader("X-All");
        assertEquals("All", header.getValue());

        header = response.getFirstHeader("X-Management");
        assertEquals("Management", header.getValue());

        header = response.getFirstHeader("X-Error");
        assertNull("Header X-Error Unexpected", header);
    }

    /**
     * Test that a call to the '/error' endpoint returns the expected headers.
     */
    @Test
    public void testError() throws Exception {
        activateHeaders();

        HttpGet get = new HttpGet(errorUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        Header header = response.getFirstHeader("X-All");
        assertEquals("All", header.getValue());

        header = response.getFirstHeader("X-Error");
        assertEquals("Error", header.getValue());

        header = response.getFirstHeader("X-Management");
        assertNull("Header X-Management Unexpected", header);
    }

    @Test
    public void testBasic() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap("TestHeader", "TestValue"));
        headers.add(Collections.singletonMap("TestHeader2", "TestValue2"));

        headersMap.put("/management", headers);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        Header header = response.getFirstHeader("TestHeader");
        assertEquals("TestValue", header.getValue());

        header = response.getFirstHeader("TestHeader2");
        assertEquals("TestValue2", header.getValue());
    }

    @Test
    public void testMultipleValues() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap("TestHeader", "TestValue"));
        headers.add(Collections.singletonMap("TestHeader", "TestValue2"));

        headersMap.put("/management", headers);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        Header[] headerArray = response.getHeaders("TestHeader");

        List<Header> headerList = Arrays.asList(headerArray);

        assertEquals(2, headerList.size());
        headerList.contains(new BasicHeader("TestHeader", "TestValue"));
        headerList.contains(new BasicHeader("TestHeader", "TestValue2"));
    }

    @Test
    public void testDuplicateValues() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap("TestHeader", "TestValue"));
        headers.add(Collections.singletonMap("TestHeader", "TestValue"));

        headersMap.put("/management", headers);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        Header[] headerArray = response.getHeaders("TestHeader");

        List<Header> headerList = Arrays.asList(headerArray);

        assertEquals(2, headerList.size());
        headerList.contains(new BasicHeader("TestHeader", "TestValue"));
    }

    @Test
    public void testRootHeadersAppliesToOtherEndpoints() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap("TestHeader", "TestValue"));
        headers.add(Collections.singletonMap("TestHeader2", "TestValue2"));

        headersMap.put("/", headers);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        Header header = response.getFirstHeader("TestHeader");
        assertEquals("TestValue", header.getValue());

        header = response.getFirstHeader("TestHeader2");
        assertEquals("TestValue2", header.getValue());

        get = new HttpGet(errorUrl.toURI().toString());
        response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        header = response.getFirstHeader("TestHeader");
        assertEquals("TestValue", header.getValue());

        header = response.getFirstHeader("TestHeader2");
        assertEquals("TestValue2", header.getValue());
    }

    @Test
    public void testRootHeadersCombineWithManagementHeadersWithDifferentName() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap("TestHeader", "TestValue"));

        List<Map<String, String>> headers2 = new LinkedList<>();
        headers2.add(Collections.singletonMap("TestHeader2", "TestValue2"));

        headersMap.put("/", headers);
        headersMap.put("/management", headers2);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        Header header = response.getFirstHeader("TestHeader");
        assertEquals("TestValue", header.getValue());

        header = response.getFirstHeader("TestHeader2");
        assertEquals("TestValue2", header.getValue());

        get = new HttpGet(errorUrl.toURI().toString());
        response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        header = response.getFirstHeader("TestHeader");
        assertEquals("TestValue", header.getValue());

        header = response.getFirstHeader("TestHeader2");
        assertNull(header);
    }

    @Test
    public void testRootHeadersCombineWithManagementHeadersWithSameName() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap("TestHeader", "TestValue"));

        List<Map<String, String>> headers2 = new LinkedList<>();
        headers2.add(Collections.singletonMap("TestHeader", "TestValue2"));

        headersMap.put("/", headers);
        headersMap.put("/management", headers2);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        Header[] headerArray = response.getHeaders("TestHeader");

        List<Header> headerList = Arrays.asList(headerArray);

        assertEquals(2, headerList.size());
        headerList.contains(new BasicHeader("TestHeader", "TestValue"));
        headerList.contains(new BasicHeader("TestHeader", "TestValue2"));

        get = new HttpGet(errorUrl.toURI().toString());
        response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        headerArray = response.getHeaders("TestHeader");

        headerList = Arrays.asList(headerArray);

        assertEquals(1, headerList.size());
        headerList.contains(new BasicHeader("TestHeader", "TestValue"));
    }

    @Test
    public void testRootDuplicateHeadersCombineWithManagementHeaders() throws Exception {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();

        List<Map<String, String>> headers = new LinkedList<>();
        headers.add(Collections.singletonMap("TestHeader", "TestValue"));

        List<Map<String, String>> headers2 = new LinkedList<>();
        headers2.add(Collections.singletonMap("TestHeader", "TestValue"));

        headersMap.put("/", headers);
        headersMap.put("/management", headers2);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        Header[] headerArray = response.getHeaders("TestHeader");

        List<Header> headerList = Arrays.asList(headerArray);

        assertEquals(2, headerList.size());
        assertEquals("TestHeader", headerList.get(0).getName());
        assertEquals("TestValue", headerList.get(0).getValue());
        assertEquals("TestHeader", headerList.get(1).getName());
        assertEquals("TestValue", headerList.get(1).getValue());

        get = new HttpGet(errorUrl.toURI().toString());
        response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        headerArray = response.getHeaders("TestHeader");

        headerList = Arrays.asList(headerArray);

        assertEquals(1, headerList.size());
        assertEquals("TestHeader", headerList.get(0).getName());
        assertEquals("TestValue", headerList.get(0).getValue());
    }

    /**
     * Test that attempt to use colon in header names correctly fail.
     */
    @Test
    public void testColonInHeaderName() {
        testBadHeaderName("X:Header", "WFLYCTL0457");
    }

    /**
     * Test that attempt to use space in header names correctly fail.
     */
    @Test
    public void testSpaceInHeaderName() {
        testBadHeaderName("X Header", "WFLYCTL0457");
    }

    /**
     * Test that attempt to use new line in header names correctly fail.
     */
    @Test
    public void testNewLineInHeaderName() {
        testBadHeaderName("X\nHeader", "WFLYCTL0457");
    }


    /**
     * Test that attempt to use disallowed header name 'Connection' correctly fail.
     */
    @Test
    public void testConnectionHeader() {
        testBadHeaderName("Connection", "WFLYCTL0458");
    }

    /**
     * Test that attempt to use disallowed header name 'Date' correctly fail.
     */
    @Test
    public void testDateHeader() {
        testBadHeaderName("Date", "WFLYCTL0458");
    }

    private void testBadHeaderName(String headerName, String errorCode) {
        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();
        headersMap.put("/", Collections.singletonList(Collections.singletonMap(headerName, "TestValue")));
        try {
            managementClient.executeForResult(createConstantHeadersOperation(headersMap));
            fail("Operation was expected to fail.");
        } catch (UnsuccessfulOperationException e) {
            assertTrue(e.getMessage().contains(errorCode));
        }
    }

    @Test
    public void testHeadersNotOverridden() throws Exception {
        HttpGet get = new HttpGet(managementUrl.toURI().toString());
        HttpResponse response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        Header[] headerArray = response.getAllHeaders();

        List<Header> headerList = Arrays.stream(headerArray)
              .filter(header -> !header.getName().equals("Connection") && !header.getName().equals("Date")
                    && !header.getName().equals("Transfer-Encoding"))
              .collect(Collectors.toList());

        Map<String, List<Map<String, String>>> headersMap = new HashMap<>();
        List<Map<String, String>> headers = new LinkedList<>();

        for (Header header : headerList) {
            headers.add(Collections.singletonMap(header.getName(), "TestValue"));
        }

        headersMap.put("/management", headers);

        managementClient.executeForResult(createConstantHeadersOperation(headersMap));
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        get = new HttpGet(managementUrl.toURI().toString());
        response = httpClient.execute(get);
        assertTrue(response.getStatusLine().getStatusCode() == 200);

        for (Header header : headerList) {
            assertNotEquals("TestValue", response.getFirstHeader(header.getName()).getValue());
            assertEquals(header.getValue(), response.getFirstHeader(header.getName()).getValue()); // won't this break?
        }
    }
}
