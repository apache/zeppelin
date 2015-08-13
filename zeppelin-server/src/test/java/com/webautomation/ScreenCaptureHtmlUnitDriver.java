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

package com.webautomation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.internal.Base64Encoder;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebWindow;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * from https://code.google.com/p/selenium/issues/detail?id=1361
 */
public class ScreenCaptureHtmlUnitDriver extends HtmlUnitDriver implements TakesScreenshot {

    private static Map<String, byte[]> imagesCache = Collections.synchronizedMap(new HashMap<String, byte[]>());

    private static Map<String, String> cssjsCache = Collections.synchronizedMap(new HashMap<String, String>());

    // http://stackoverflow.com/questions/4652777/java-regex-to-get-the-urls-from-css
    private final static Pattern cssUrlPattern = Pattern.compile("background(-image)?[\\s]*:[^url]*url[\\s]*\\([\\s]*([^\\)]*)[\\s]*\\)[\\s]*");// ?<url>

    public ScreenCaptureHtmlUnitDriver() {
        super();
    }

    public ScreenCaptureHtmlUnitDriver(boolean enableJavascript) {
        super(enableJavascript);
    }

    public ScreenCaptureHtmlUnitDriver(Capabilities capabilities) {
        super(capabilities);
    }

    public ScreenCaptureHtmlUnitDriver(BrowserVersion version) {
        super(version);
        DesiredCapabilities var = ((DesiredCapabilities) getCapabilities());
        var.setCapability(CapabilityType.TAKES_SCREENSHOT, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X> X getScreenshotAs(OutputType<X> target) throws WebDriverException {
        byte[] archive = new byte[0];
        try {
            archive = downloadCssAndImages(getWebClient(), (HtmlPage) getCurrentWindow().getEnclosedPage());
        } catch (Exception e) {
        }
        if(target.equals(OutputType.BASE64)){
            return target.convertFromBase64Png(new Base64Encoder().encode(archive));
        }
        if(target.equals(OutputType.FILE)){
            File f = new File("screen.tmp");
            try {
                FileOutputStream scr = new FileOutputStream(f);
                scr.write(archive);
                scr.close();
            } catch (IOException e) {
                throw new WebDriverException(e);
            }
            return (X) f;
        }
        return (X) archive;
    }

    // http://stackoverflow.com/questions/2244272/how-can-i-tell-htmlunits-webclient-to-download-images-and-css
    protected byte[] downloadCssAndImages(WebClient webClient, HtmlPage page) throws Exception {
        WebWindow currentWindow = webClient.getCurrentWindow();
        Map<String, String> urlMapping = new HashMap<String, String>();
        Map<String, byte[]> files = new HashMap<String, byte[]>();
        WebWindow window = null;
        try {
            window = webClient.getWebWindowByName(page.getUrl().toString()+"_screenshot");
            webClient.getPage(window, new WebRequest(page.getUrl()));
        } catch (Exception e) {
            window = webClient.openWindow(page.getUrl(), page.getUrl().toString()+"_screenshot");
        }

        String xPathExpression = "//*[name() = 'img' or name() = 'link' and (@type = 'text/css' or @type = 'image/x-icon') or  @type = 'text/javascript']";
        List<?> resultList = page.getByXPath(xPathExpression);

        Iterator<?> i = resultList.iterator();
        while (i.hasNext()) {
            try {
                HtmlElement el = (HtmlElement) i.next();
                String resourceSourcePath = el.getAttribute("src").equals("") ? el.getAttribute("href") : el
                        .getAttribute("src");
                if (resourceSourcePath == null || resourceSourcePath.equals(""))
                    continue;
                URL resourceRemoteLink = page.getFullyQualifiedUrl(resourceSourcePath);
                String resourceLocalPath = mapLocalUrl(page, resourceRemoteLink, resourceSourcePath, urlMapping);
                urlMapping.put(resourceSourcePath, resourceLocalPath);
                if (!resourceRemoteLink.toString().endsWith(".css")) {
                    byte[] image = downloadImage(webClient, window, resourceRemoteLink);
                    files.put(resourceLocalPath, image);
                } else {
                    String css = downloadCss(webClient, window, resourceRemoteLink);
                    for (String cssImagePath : getLinksFromCss(css)) {
                        URL cssImagelink = page.getFullyQualifiedUrl(cssImagePath.replace("\"", "").replace("\'", "")
                                .replace(" ", ""));
                        String cssImageLocalPath = mapLocalUrl(page, cssImagelink, cssImagePath, urlMapping);
                        files.put(cssImageLocalPath, downloadImage(webClient, window, cssImagelink));
                    }
                    files.put(resourceLocalPath, replaceRemoteUrlsWithLocal(css, urlMapping)
                            .replace("resources/", "./").getBytes());
                }
            } catch (Exception e) {
            }
        }
        String pagesrc = replaceRemoteUrlsWithLocal(page.getWebResponse().getContentAsString(), urlMapping);
        files.put("page.html", pagesrc.getBytes());
        webClient.setCurrentWindow(currentWindow);
        return createZip(files);
    }

    String downloadCss(WebClient webClient, WebWindow window, URL resourceUrl) throws Exception {
        if (cssjsCache.get(resourceUrl.toString()) == null) {
            cssjsCache.put(resourceUrl.toString(), webClient.getPage(window, new WebRequest(resourceUrl))
                    .getWebResponse().getContentAsString());

        }
        return cssjsCache.get(resourceUrl.toString());
    }

    byte[] downloadImage(WebClient webClient, WebWindow window, URL resourceUrl) throws Exception {
        if (imagesCache.get(resourceUrl.toString()) == null) {
            imagesCache.put(
                    resourceUrl.toString(),
                    IOUtils.toByteArray(webClient.getPage(window, new WebRequest(resourceUrl)).getWebResponse()
                            .getContentAsStream()));
        }
        return imagesCache.get(resourceUrl.toString());
    }

    public static byte[] createZip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zipfile = new ZipOutputStream(bos);
        Iterator<String> i = files.keySet().iterator();
        String fileName = null;
        ZipEntry zipentry = null;
        while (i.hasNext()) {
            fileName = i.next();
            zipentry = new ZipEntry(fileName);
            zipfile.putNextEntry(zipentry);
            zipfile.write(files.get(fileName));
        }
        zipfile.close();
        return bos.toByteArray();
    }

    List<String> getLinksFromCss(String css) {
        List<String> result = new LinkedList<String>();
        Matcher m = cssUrlPattern.matcher(css);
        while (m.find()) { // find next match
            result.add( m.group(2));
        }
        return result;
    }

    String replaceRemoteUrlsWithLocal(String source, Map<String, String> replacement) {
        for (String object : replacement.keySet()) {
            // background:url(http://org.com/images/image.gif)
            source = source.replace(object, replacement.get(object));
        }
        return source;
    }

    String mapLocalUrl(HtmlPage page, URL link, String path, Map<String, String> replacementToAdd) throws Exception {
        String resultingFileName = "resources/" + FilenameUtils.getName(link.getFile());
        replacementToAdd.put(path, resultingFileName);
        return resultingFileName;
    }

}