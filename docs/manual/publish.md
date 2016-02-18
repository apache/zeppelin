---
layout: page
title: "Publish your Paragraph"
description: ""
group: manual
---
<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
{% include JB/setup %}

## How can you publish your paragraph ?
Zeppelin provides a feature for publishing your notebook paragraph results. Using this feature, you can show Zeppelin notebook paragraph results in your own website. 
It's very straightforward. Just use `<iframe>` tag in your page.

> **Warning**: Please use this feature with caution and in a trusted environment only, as Zeppelin entire Webapp could be accessible for whoever visits your website.

### Copy a Paragraph Link
A first step to publish your paragraph result is **Copy a Paragraph Link**.

  * After running a paragraph in your Zeppelin notebook, click a gear button located on the right side. Then, click **Link this Paragraph** menu like below image.
<center><img src="../assets/themes/zeppelin/img/docs-img/link-the-paragraph.png" height="100%" width="100%"></center>
  
  * Just copy the provided link. 
<center><img src="../assets/themes/zeppelin/img/docs-img/copy-the-link.png" height="100%" width="100%"></center>

### Embed the Paragraph to Your Website
For publishing the copied paragraph, you may use `<iframe>` tag in your website page.
For example,

```
<iframe src="http://< ip-address >:< port >/#/notebook/2B3QSZTKR/paragraph/...?asIframe" height="" width="" ></iframe>
```

Don't forget to also add below lines in your page for loading the Twitter Bootstrap themes and CSS used for Zeppelin notebook style, or your paragraph will be shown ugly.

```
<head>
     ....
    <link rel="stylesheet" href="zeppelin-web/bower_components/bootstrap/dist/css/bootstrap.css" />
    <link rel="stylesheet" href="zeppelin-web/fonts/font-awesome.min.css">
    ....
</head>
```

Finally, you can show off your beautiful visualization results in your website. 
<center><img src="../assets/themes/zeppelin/img/docs-img/your-website.png" height="90%" width="90%"></center>

> **Note**: To embed the paragraph in a website, Zeppelin needs to be reachable by that website. 

## Troubleshooting
Regarding to this feature, a bug was reported by [ZEPPELIN-413](https://issues.apache.org/jira/browse/ZEPPELIN-413) before. 
It says, when you click **Link this paragraph** menu, it always redirects to notebook page. 
So after then, this issue was fixed by [Pull Request #464](https://github.com/apache/incubator-zeppelin/pull/464) and the stable feature was included to **0.5.6-incubating release**.

If you have been troubled with this issue, please check your **Zeppelin release version**.
