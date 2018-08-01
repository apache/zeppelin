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

package org.apache.zeppelin.sap.universe;

import com.sun.org.apache.xpath.internal.NodeSet;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Client for API  SAP Universe
 */
public class UniverseClient {

  private static Logger logger = LoggerFactory.getLogger(UniverseClient.class);
  private static final String TOKEN_HEADER = "X-SAP-LogonToken";
  private static final String EL_FOLDER = "folder";
  private static final String EL_ITEM = "item";
  private static final String EL_NAME = "name";
  private static final String EL_PATH = "path";
  private static final String EL_ID = "id";
  private static final String EL_TECH_NAME = "technicalName";
  private static final String EL_ANSWER = "answer";
  private static final String EL_INFO = "info";
  private Map<String, String> tokens = new HashMap();
  private static final long DAY = 1000 * 60 * 60 * 24;
  private CloseableHttpClient httpClient;
  private String user;
  private String password;
  private String apiUrl;
  private String authType;
  private Header[] commonHeaders = {
    new BasicHeader("Accept", "application/xml"),
    new BasicHeader("Content-Type", "application/xml")
  };
  // <name, id>
  private final Map<String, UniverseInfo> universesMap = new ConcurrentHashMap();
  private final Map<String, Map<String, UniverseNodeInfo>> universeInfosMap =
      new ConcurrentHashMap();
  // for update the data (which was not updated a long time)
  private long universesUpdated = 0;
  private Map<String, Long> universesInfoUpdatedMap = new HashMap<>();

  private final String loginRequestTemplate = "<attrs xmlns=\"http://www.sap.com/rws/bip\">\n"
      + "<attr name=\"userName\" type=\"string\">%s</attr>\n"
      + "<attr name=\"password\" type=\"string\">%s</attr>\n"
      + "<attr name=\"auth\" type=\"string\" "
      + "possibilities=\"secEnterprise,secLDAP,secWinAD,secSAPR3\">%s</attr>\n" + "</attrs>";
  private final String createQueryRequestTemplate =
      "<query xmlns=\"http://www.sap.com/rws/sl/universe\" dataSourceType=\"%s\" " +
          "dataSourceId=\"%s\">\n" +
          "<querySpecification version=\"1.0\">\n" +
          "   <queryOptions>\n" +
          "            <queryOption name=\"duplicatedRows\" value=\"%s\"/>\n" +
          "            <queryOption name=\"maxRowsRetrieved\" activated=\"%s\" value=\"%d\"/>\n" +
          "  </queryOptions>" +
          "  <queryData>\n%s\n" +
          "     %s\n" +
          "  </queryData>\n" +
          "</querySpecification>\n" +
          "</query>\n";
  private final String filterPartTemplate = "<filterPart>%s\n</filterPart>";
  private final String errorMessageTemplate = "%s\n\n%s";
  private final String parameterTemplate = "<parameter type=\"prompt\">\n" +
      "%s\n" +
      "%s\n" +
      "%s\n" +
      "%s\n" +
      "</parameter>\n";
  private final String parameterAnswerTemplate = "<answer constrained=\"%s\" type=\"%s\">\n" +
      "            <info cardinality=\"%s\" keepLastValues=\"%s\"></info>\n" +
      "               <values>\n" + "     " +
      "                 <value>%s</value>\n" +
      "              </values>\n" +
      "        </answer>\n";

  public UniverseClient(String user, String password, String apiUrl, String authType,
                        int queryTimeout) {
    RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(queryTimeout)
        .setSocketTimeout(queryTimeout)
        .build();
    PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
    cm.setMaxTotal(100);
    cm.setDefaultMaxPerRoute(100);
    cm.closeIdleConnections(10, TimeUnit.MINUTES);
    httpClient = HttpClientBuilder.create()
        .setConnectionManager(cm)
        .setDefaultRequestConfig(requestConfig)
        .build();

    this.user = user;
    this.password = password;
    this.authType = authType;
    if (StringUtils.isNotBlank(apiUrl)) {
      this.apiUrl = apiUrl.replaceAll("/$", "");
    }
  }

  public void close() throws UniverseException {
    for (String s : tokens.keySet()) {
      closeSession(s);
    }
    try {
      httpClient.close();
    } catch (Exception e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient " +
          "(close all): Error close HTTP client", ExceptionUtils.getStackTrace(e)));
    }

  }

  public String createQuery(String token, UniverseQuery query) throws UniverseException {
    try {
      HttpPost httpPost = new HttpPost(String.format("%s%s", apiUrl, "/sl/v1/queries"));
      setHeaders(httpPost, token);
      String where = StringUtils.isNotBlank(query.getWhere()) ?
          String.format(filterPartTemplate, query.getWhere()) : StringUtils.EMPTY;
      httpPost.setEntity(new StringEntity(
          String.format(createQueryRequestTemplate, query.getUniverseInfo().getType(),
              query.getUniverseInfo().getId(), query.getDuplicatedRows(),
              query.getMaxRowsRetrieved().isPresent(), query.getMaxRowsRetrieved().orElse(0),
              query.getSelect(), where), "UTF-8"));
      HttpResponse response = httpClient.execute(httpPost);

      if (response.getStatusLine().getStatusCode() == 200) {
        return getValue(EntityUtils.toString(response.getEntity()), "//success/id");
      }

      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
          + "(create query): Request failed\n", EntityUtils.toString(response.getEntity())));
    } catch (IOException e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
          + "(create query): Request failed", ExceptionUtils.getStackTrace(e)));
    } catch (ParserConfigurationException | SAXException | XPathExpressionException e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
          + "(create query): Response processing failed", ExceptionUtils.getStackTrace(e)));
    }
  }

  public void deleteQuery(String token, String queryId) throws UniverseException {
    try {
      if (StringUtils.isNotBlank(queryId)) {
        HttpDelete httpDelete = new HttpDelete(String.format("%s%s%s", apiUrl, "/sl/v1/queries/",
            queryId));
        setHeaders(httpDelete, token);
        httpClient.execute(httpDelete);
      }
    } catch (Exception e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient " +
          "(delete query): Request failed", ExceptionUtils.getStackTrace(e)));
    }
  }

  public List<List<String>> getResults(String token, String queryId) throws UniverseException {
    HttpGet httpGet = new HttpGet(String.format("%s%s%s%s", apiUrl, "/sl/v1/queries/",
        queryId, "/data.svc/Flows0"));
    setHeaders(httpGet, token);
    HttpResponse response = null;
    try {
      response = httpClient.execute(httpGet);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
            + "(get results): Request failed\n", EntityUtils.toString(response.getEntity())));
      }
    } catch (IOException e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient " +
          "(get results): Request failed", ExceptionUtils.getStackTrace(e)));
    }

    try (InputStream xmlStream = response.getEntity().getContent()) {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(xmlStream);
      XPathFactory xPathfactory = XPathFactory.newInstance();
      XPath xpath = xPathfactory.newXPath();
      XPathExpression expr = xpath.compile("//feed/entry/content/properties");
      NodeList resultsNodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      if (resultsNodes != null) {
        return parseResults(resultsNodes);
      } else {
        throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
            + "(get results): Response processing failed"));
      }
    } catch (IOException e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
          + "(get results): Request failed", ExceptionUtils.getStackTrace(e)));
    } catch (ParserConfigurationException | SAXException | XPathExpressionException e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
          + "(get results): Response processing failed", ExceptionUtils.getStackTrace(e)));
    }
  }

  public String getToken(String paragraphId) throws UniverseException {
    try {
      if (tokens.containsKey(paragraphId)) {
        return tokens.get(paragraphId);
      }
      HttpPost httpPost = new HttpPost(String.format("%s%s", apiUrl, "/logon/long"));
      setHeaders(httpPost);

      httpPost.setEntity(new StringEntity(
          String.format(loginRequestTemplate, user, password, authType), "UTF-8"));
      HttpResponse response = httpClient.execute(httpPost);
      String result = null;
      if (response.getStatusLine().getStatusCode() == 200) {
        result = getValue(EntityUtils.toString(response.getEntity()),
            "//content/attrs/attr[@name=\"logonToken\"]");
        tokens.put(paragraphId, result);
      }

      return result;
    } catch (IOException e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
          + "(get token): Request failed", ExceptionUtils.getStackTrace(e)));
    } catch (ParserConfigurationException | SAXException | XPathExpressionException e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
          + "(get token): Response processing failed", ExceptionUtils.getStackTrace(e)));
    }
  }

  public boolean closeSession(String paragraphId) throws UniverseException {
    try {
      if (tokens.containsKey(paragraphId)) {
        HttpPost httpPost = new HttpPost(String.format("%s%s", apiUrl, "/logoff"));
        setHeaders(httpPost, tokens.get(paragraphId));
        HttpResponse response = httpClient.execute(httpPost);
        if (response.getStatusLine().getStatusCode() == 200) {
          return true;
        }
      }

      return false;
    } catch (Exception e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
          + "(close session): Request failed", ExceptionUtils.getStackTrace(e)));
    } finally {
      tokens.remove(paragraphId);
    }
  }

  public UniverseInfo getUniverseInfo(String universeName) {
    return universesMap.get(universeName);
  }

  public Map<String, UniverseNodeInfo> getUniverseNodesInfo(String token, String universeName)
      throws UniverseException {
    UniverseInfo universeInfo = universesMap.get(universeName);
    if (universeInfo != null && StringUtils.isNotBlank(universeInfo.getId())) {
      Map<String, UniverseNodeInfo> universeNodeInfoMap = universeInfosMap.get(universeName);
      if (universeNodeInfoMap != null && universesInfoUpdatedMap.containsKey(universeName) &&
          !isExpired(universesInfoUpdatedMap.get(universeName))) {
        return universeNodeInfoMap;
      } else {
        universeNodeInfoMap = new HashMap<>();
      }
      try {
        HttpGet httpGet =
            new HttpGet(String.format("%s%s%s", apiUrl, "/sl/v1/universes/", universeInfo.getId()));
        setHeaders(httpGet, token);
        HttpResponse response = httpClient.execute(httpGet);

        if (response.getStatusLine().getStatusCode() == 200) {
          try (InputStream xmlStream = response.getEntity().getContent()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlStream);
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            XPathExpression expr = xpath.compile("//outline/folder");
            XPathExpression exprRootItems = xpath.compile("//outline/item");
            NodeList universeInfoNodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
            NodeList universeRootInfoNodes =
                (NodeList) exprRootItems.evaluate(doc, XPathConstants.NODESET);
            if (universeInfoNodes != null) {
              parseUniverseInfo(universeInfoNodes, universeNodeInfoMap);
            }
            if (universeRootInfoNodes != null) {
              parseUniverseInfo(universeRootInfoNodes, universeNodeInfoMap);
            }
          } catch (Exception e) {
            throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
                    + "(get universe nodes info): Response processing failed",
                ExceptionUtils.getStackTrace(e)));
          }
        }
      } catch (IOException e) {
        throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
            + "(get universe nodes info): Request failed", ExceptionUtils.getStackTrace(e)));
      }
      universeInfosMap.put(universeName, universeNodeInfoMap);
      universesInfoUpdatedMap.put(universeName, System.currentTimeMillis());

      return universeNodeInfoMap;
    }
    return Collections.emptyMap();

  }

  public void loadUniverses(String token) throws UniverseException {
    if (universesMap.isEmpty() || universesUpdated == 0 || isExpired(universesUpdated)) {
      Map<String, UniverseInfo> universes = new ConcurrentHashMap();
      loadUniverses(token, 0, universes);
      universesMap.clear();
      universesMap.putAll(universes);
      universesUpdated = System.currentTimeMillis();
    }
  }

  public void cleanUniverses() {
    universesMap.clear();
  }

  public void removeUniverseInfo(String universe) {
    universeInfosMap.remove(universe);
  }

  public Map<String, UniverseInfo> getUniversesMap() {
    return universesMap;
  }

  public List<UniverseQueryPrompt> getParameters(String token, String queryId)
      throws UniverseException {
    HttpGet httpGet = new HttpGet(String.format("%s%s%s%s", apiUrl, "/sl/v1/queries/",
        queryId, "/parameters"));
    setHeaders(httpGet, token);
    HttpResponse response = null;
    try {
      response = httpClient.execute(httpGet);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
            + "(get parameters): Request failed\n", EntityUtils.toString(response.getEntity())));
      }
    } catch (IOException e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient " +
          "(get parameters): Request failed", ExceptionUtils.getStackTrace(e)));
    }

    try (InputStream xmlStream = response.getEntity().getContent()) {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(xmlStream);
      XPathFactory xPathfactory = XPathFactory.newInstance();
      XPath xpath = xPathfactory.newXPath();
      XPathExpression expr = xpath.compile("//parameters/parameter");
      NodeList parametersNodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      if (parametersNodes != null) {
        return parseParameters(parametersNodes);
      } else {
        throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
            + "(get parameters): Response processing failed"));
      }
    } catch (IOException e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
          + "(get parameters): Response processing failed", ExceptionUtils.getStackTrace(e)));
    } catch (ParserConfigurationException | SAXException | XPathExpressionException e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
          + "(get parameters): Response processing failed", ExceptionUtils.getStackTrace(e)));
    }
  }

  public void setParametersValues(String token, String queryId,
                                  List<UniverseQueryPrompt> parameters) throws UniverseException {
    HttpPut httpPut = new HttpPut(String.format("%s%s%s%s", apiUrl, "/sl/v1/queries/",
        queryId, "/parameters"));
    setHeaders(httpPut, token);
    HttpResponse response = null;
    try {
      StringBuilder request = new StringBuilder();
      request.append("<parameters>\n");
      for (UniverseQueryPrompt parameter : parameters) {
        String answer = String.format(parameterAnswerTemplate, parameter.getConstrained(),
            parameter.getType(), parameter.getCardinality(), parameter.getKeepLastValues(),
            parameter.getValue());
        String id = parameter.getId() != null ? String.format("<id>%s</id>\n", parameter.getId()) :
            StringUtils.EMPTY;
        String technicalName = parameter.getTechnicalName() != null ?
            String.format("<technicalName>%s</technicalName>\n", parameter.getTechnicalName()) :
            StringUtils.EMPTY;
        String name = parameter.getTechnicalName() != null ?
            String.format("<name>%s</name>\n", parameter.getName()) :
            StringUtils.EMPTY;
        request.append(String.format(parameterTemplate, id, technicalName, name, answer));
      }
      request.append("</parameters>\n");

      httpPut.setEntity(new StringEntity(request.toString(), "UTF-8"));

      response = httpClient.execute(httpPut);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
            + "(set parameters): Request failed\n", EntityUtils.toString(response.getEntity())));
      }
    } catch (IOException e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient " +
          "(set parameters): Request failed", ExceptionUtils.getStackTrace(e)));
    }
  }

  private void loadUniverses(String token, int offset, Map<String, UniverseInfo> universesMap)
      throws UniverseException {
    int limit = 50;
    HttpGet httpGet = new HttpGet(String.format("%s%s?offset=%s&limit=%s", apiUrl,
        "/sl/v1/universes",
        offset, limit));
    setHeaders(httpGet, token);
    HttpResponse response = null;
    try {
      response = httpClient.execute(httpGet);
    } catch (Exception e) {
      throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
          + "(get universes): Request failed", ExceptionUtils.getStackTrace(e)));
    }
    if (response != null && response.getStatusLine().getStatusCode() == 200) {
      try (InputStream xmlStream = response.getEntity().getContent()) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlStream);
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        XPathExpression expr = xpath.compile("//universe");
        NodeList universesNodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        if (universesNodes != null) {
          int count = universesNodes.getLength();
          for (int i = 0; i < count; i++) {
            Node universe = universesNodes.item(i);
            if (universe.hasChildNodes()) {
              NodeList universeParameters = universe.getChildNodes();
              int parapetersCount = universeParameters.getLength();
              String id = null;
              String name = null;
              String type = null;
              for (int j = 0; j < parapetersCount; j++) {
                Node parameterNode = universeParameters.item(j);
                parameterNode.getNodeName();
                if (parameterNode.getNodeType() == Node.ELEMENT_NODE) {
                  if (parameterNode.getNodeName().equalsIgnoreCase("id")) {
                    id = parameterNode.getTextContent();
                    continue;
                  }
                  if (parameterNode.getNodeName().equalsIgnoreCase("name")) {
                    name = parameterNode.getTextContent();
                    continue;
                  }
                  if (parameterNode.getNodeName().equalsIgnoreCase("type")) {
                    type = parameterNode.getTextContent();
                    continue;
                  }
                }
              }
              if (StringUtils.isNotBlank(type)) {
                name = name.replaceAll(String.format("\\.%s$", type), StringUtils.EMPTY);
              }
              universesMap.put(name, new UniverseInfo(id, name, type));
            }
          }
          if (count == limit) {
            offset += limit;
            loadUniverses(token, offset, universesMap);
          }
        }
      } catch (IOException e) {
        throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
            + "(get universes): Response processing failed", ExceptionUtils.getStackTrace(e)));
      } catch (ParserConfigurationException | SAXException | XPathExpressionException e) {
        throw new UniverseException(String.format(errorMessageTemplate, "UniverseClient "
            + "(get universes): Response processing failed", ExceptionUtils.getStackTrace(e)));
      }
    }
  }

  private boolean isExpired(Long lastUpdated) {
    if (lastUpdated == null || System.currentTimeMillis() - lastUpdated > DAY) {
      return true;
    }

    return false;
  }

  private void setHeaders(HttpRequestBase request) {
    setHeaders(request, null);
  }

  private void setHeaders(HttpRequestBase request, String token) {
    request.setHeaders(commonHeaders);
    if (StringUtils.isNotBlank(token)) {
      request.addHeader(TOKEN_HEADER, token);
    }
  }

  private String getValue(String response, String xPathString) throws ParserConfigurationException,
      IOException, SAXException, XPathExpressionException {
    try (InputStream xmlStream = new ByteArrayInputStream(response.getBytes())) {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(xmlStream);
      XPathFactory xPathfactory = XPathFactory.newInstance();
      XPath xpath = xPathfactory.newXPath();
      XPathExpression expr = xpath.compile(xPathString);
      Node tokenNode = (Node) expr.evaluate(doc, XPathConstants.NODE);
      if (tokenNode != null) {
        return tokenNode.getTextContent();
      }
    }
    return null;
  }

  private List<UniverseQueryPrompt> parseParameters(NodeList parametersNodeList) {
    List<UniverseQueryPrompt> parameters = new ArrayList<>();
    if (parametersNodeList != null) {
      int count = parametersNodeList.getLength();
      for (int i = 0; i < count; i++) {
        Node parameterNode = parametersNodeList.item(i);
        Node type = parameterNode.getAttributes().getNamedItem("type");
        if (type != null && type.getTextContent().equalsIgnoreCase("prompt") &&
            parameterNode.hasChildNodes()) {
          NodeList parameterInfoNodes = parameterNode.getChildNodes();
          int childNodesCount = parameterInfoNodes.getLength();
          String name = null;
          Integer id = null;
          String cardinality = null;
          String constrained = null;
          String valueType = null;
          String technicalName = null;
          String keepLastValues = null;
          for (int j = 0; j < childNodesCount; j++) {
            Node childNode = parameterInfoNodes.item(j);
            String childNodeName = childNode.getNodeName();
            if (childNodeName.equalsIgnoreCase(EL_NAME)) {
              name = childNode.getTextContent();
              continue;
            }
            if (childNodeName.equalsIgnoreCase(EL_ID)) {
              id = Integer.parseInt(childNode.getTextContent());
              continue;
            }
            if (childNodeName.equalsIgnoreCase(EL_TECH_NAME)) {
              technicalName = childNode.getTextContent();
              continue;
            }
            if (childNodeName.equalsIgnoreCase(EL_ANSWER)) {
              NamedNodeMap answerAttributes = childNode.getAttributes();
              if (answerAttributes.getNamedItem("constrained") != null) {
                constrained = answerAttributes.getNamedItem("constrained").getTextContent();
              }
              if (answerAttributes.getNamedItem("type") != null) {
                valueType = answerAttributes.getNamedItem("type").getTextContent();
              }
              NodeList answerNodes = childNode.getChildNodes();
              int answerCount = answerNodes.getLength();
              for (int k = 0; k < answerCount; k++) {
                Node answerChildNode = answerNodes.item(k);
                String answerChildNodeName = answerChildNode.getNodeName();
                if (answerChildNodeName.equalsIgnoreCase(EL_INFO)) {
                  NamedNodeMap infoAttributes = answerChildNode.getAttributes();
                  if (infoAttributes.getNamedItem("cardinality") != null) {
                    cardinality = infoAttributes.getNamedItem("cardinality").getTextContent();
                  }
                  if (infoAttributes.getNamedItem("keepLastValues") != null) {
                    keepLastValues = infoAttributes.getNamedItem("keepLastValues").getTextContent();
                  }
                  break;
                }
              }
              continue;
            }
          }
          if (name != null && id != null && cardinality != null) {
            parameters.add(new UniverseQueryPrompt(id, name, cardinality, constrained, valueType,
                technicalName, keepLastValues));
            break;
          }
        }
      }
    }

    return parameters;
  }

  private List<List<String>> parseResults(NodeList resultsNodeList) {
    List<List<String>> results = new ArrayList<>();
    if (resultsNodeList != null) {
      int count = resultsNodeList.getLength();
      for (int i = 0; i < count; i++) {
        Node node = resultsNodeList.item(i);
        if (node.getNodeType() == Node.ELEMENT_NODE && node.hasChildNodes()) {
          NodeList properties = node.getChildNodes();
          if (properties != null) {
            int countProperties = properties.getLength();
            List<String> headers = new ArrayList<>();
            List<String> row = new ArrayList<>();
            // first property is id
            for (int j = 1; j < countProperties; j++) {
              Node propertyNode = properties.item(j);
              if (i == 0) {
                headers.add(propertyNode.getNodeName().replaceAll("^\\w*:", StringUtils.EMPTY));
              }
              row.add(propertyNode.getTextContent());
            }
            if (i == 0) {
              results.add(headers);
            }
            results.add(row);
          }
        }
      }
    }

    return results;
  }

  private void addAttributesToDimension(Node universeNode, Map<String, UniverseNodeInfo> nodes) {
    final NodeList attributeNodes = universeNode.getChildNodes();
    final int attributeNodesCount = attributeNodes.getLength();

    for (int i = 0; i < attributeNodesCount; ++i) {
      final Node attributeNode = attributeNodes.item(i);

      if (attributeNode.getNodeName().equalsIgnoreCase(EL_ITEM)) {
        final NodeList childNodes = attributeNode.getChildNodes();
        final int childNodesCount = childNodes.getLength();

        String nodeId = null;
        String nodeName = null;
        String nodePath = null;
        for (int j = 0; j < childNodesCount; j++) {
          Node childNode = childNodes.item(j);
          if (childNode.getNodeType() == Node.ELEMENT_NODE) {
            switch (childNode.getNodeName().toLowerCase()) {
              case EL_NAME:
                nodeName = childNode.getTextContent();
                break;
              case EL_ID:
                nodeId = childNode.getTextContent();
                break;
              case EL_PATH:
                nodePath = childNode.getTextContent();
                break;
            }
          }
        }
        StringBuilder key = new StringBuilder();
        if (StringUtils.isNotBlank(nodeName)) {
          String nodeType = null;
          String[] parts = nodePath.split("\\\\");
          List<String> path = new ArrayList();
          for (String part : parts) {
            String[] p = part.split("\\|");
            if (p.length == 2) {
              if (p[1].equalsIgnoreCase("folder") || p[1].equalsIgnoreCase("dimension")) {
                path.add(p[0]);
              } else {
                nodeName = p[0];
                nodeType = p[1];
              }
            }
          }
          final String folder = StringUtils.join(path, "\\");
          key.append("[");
          key.append(StringUtils.join(path, "].["));
          key.append(String.format("].[%s]", nodeName));
          nodes.put(key.toString(),
              new UniverseNodeInfo(nodeId, nodeName, nodeType, folder, nodePath));
        }
      }
    }
  }

  private void parseUniverseInfo(NodeList universeInfoNodes, Map<String, UniverseNodeInfo> nodes) {
    if (universeInfoNodes != null) {
      int count = universeInfoNodes.getLength();
      for (int i = 0; i < count; i++) {
        Node node = universeInfoNodes.item(i);
        if (node.getNodeType() == Node.ELEMENT_NODE && node.hasChildNodes()) {
          String name = node.getNodeName();
          NodeList childNodes = node.getChildNodes();
          int childNodesCount = childNodes.getLength();
          if (name.equalsIgnoreCase(EL_FOLDER)) {
            NodeSet list = new NodeSet();
            for (int j = 0; j < childNodesCount; j++) {
              Node childNode = childNodes.item(j);
              if (childNode.getNodeType() == Node.ELEMENT_NODE && childNode.hasChildNodes()) {
                String childNodeName = childNode.getNodeName();
                if (childNodeName.equalsIgnoreCase(EL_FOLDER)
                    || childNodeName.equalsIgnoreCase(EL_ITEM)) {
                  list.addNode(childNode);
                }
              }
            }
            if (list.getLength() > 0) {
              parseUniverseInfo(list, nodes);
            }
          } else if (name.equalsIgnoreCase(EL_ITEM)) {
            String nodeId = null;
            String nodeName = null;
            String nodePath = null;
            for (int j = 0; j < childNodesCount; j++) {
              Node childNode = childNodes.item(j);
              if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                String childNodeName = childNode.getNodeName();
                if (childNodeName.equalsIgnoreCase(EL_NAME)) {
                  nodeName = childNode.getTextContent();
                  continue;
                }
                if (childNodeName.equalsIgnoreCase(EL_ID)) {
                  nodeId = childNode.getTextContent();
                  continue;
                }
                if (childNodeName.equalsIgnoreCase(EL_PATH)) {
                  nodePath = childNode.getTextContent();
                  continue;
                }
              }
            }
            String folder = null;
            StringBuilder key = new StringBuilder();
            if (StringUtils.isNotBlank(nodeName)) {
              String nodeType = null;
              if (StringUtils.isNotBlank(nodePath)) {
                String[] parts = nodePath.split("\\\\");
                List<String> path = new ArrayList();
                for (String part : parts) {
                  String[] p = part.split("\\|");
                  if (p.length == 2) {
                    if (p[1].equalsIgnoreCase("folder")) {
                      path.add(p[0]);
                    } else {
                      nodeName = p[0];
                      nodeType = p[1];
                      if (p[1].equalsIgnoreCase("dimension")) {
                        addAttributesToDimension(node, nodes);
                      }
                    }
                  }
                }
                folder = StringUtils.join(path, "\\");
                if (path.isEmpty()) {
                  key.append(String.format("[%s]", nodeName));
                } else {
                  key.append("[");
                  key.append(StringUtils.join(path, "].["));
                  key.append(String.format("].[%s]", nodeName));
                }
              }
              nodes.put(key.toString(),
                  new UniverseNodeInfo(nodeId, nodeName, nodeType, folder, nodePath));
            }
          }
        }
      }
    }
  }
}
