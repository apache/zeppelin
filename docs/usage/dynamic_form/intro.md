---
layout: page
title: "Dynamic Form in Apache Zeppelin"
description: "Apache Zeppelin dynamically creates input forms. Depending on language backend, there're two different ways to create dynamic form."
group: usage/dynamic_form 
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

# What is Dynamic Form?

<div id="toc"></div>

Apache Zeppelin dynamically creates input forms. Depending on language backend, there're two different ways to create dynamic form.
Custom language backend can select which type of form creation it wants to use. Forms can have different scope (paragraph or note). 
Forms with scope "note" available in all paragraphs regardless of which paragraph has code to create these forms.

## Using form Templates (scope: paragraph)

This mode creates form using simple template language. It's simple and easy to use. For example Markdown, Shell, Spark SQL language backend uses it.

### Text input form

To create text input form, use `${formName}` templates.

for example

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/form_input.png" width="450px" />


Also you can provide default value, using `${formName=defaultValue}`.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/form_input_default.png" />

### Select form

To create select form, use `${formName=defaultValue,option1|option2...}`

for example

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/form_select.png" />

Also you can separate option's display name and value, using `${formName=defaultValue,option1(DisplayName)|option2(DisplayName)...}`

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/form_select_displayname.png" />

The paragraph will be automatically run after you change your selection by default.
But in case you have multiple types dynamic form in one paragraph, you might want to run the paragraph after changing all the selections.
You can control this by unchecking the below **Run on selection change** option in the setting menu.

Even if you uncheck this option, still you can run it by pressing `Enter`.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/selectForm-checkbox.png" />

### Checkbox form

For multi-selection, you can create a checkbox form using `${checkbox:formName=defaultValue1|defaultValue2...,option1|option2...}`. The variable will be substituted by a comma-separated string based on the selected items. For example:

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/form_checkbox.png">

You can specify the delimiter using `${checkbox(delimiter):formName=...}`:

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/form_checkbox_delimiter.png">

Like [select form](#select-form), the paragraph will be automatically run after you change your selection by default.
But in case you have multiple types dynamic form in one paragraph, you might want to run the paragraph after changing all the selections.
You can control this by unchecking the below **Run on selection change** option in the setting menu.

Even if you uncheck this option, still you can run it by pressing `Enter`.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/selectForm-checkbox.png" />

## Using form Templates (scope: note)

Has a same syntax but starts with two symbols `$`. (for ex. input `$${forName}`)

## Creates Programmatically (scope: paragraph)

Some language backends can programmatically create forms. For example [ZeppelinContext](../../interpreter/spark.html#zeppelincontext) provides a form creation API

Here are some examples:

### Text input form
<div class="codetabs">
    <div data-lang="scala" markdown="1">

{% highlight scala %}
%spark
println("Hello "+z.textbox("name"))
{% endhighlight %}

    </div>
    <div data-lang="python" markdown="1">

{% highlight python %}
%pyspark
print("Hello "+z.textbox("name"))
{% endhighlight %}

    </div>
</div>
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/form_input_prog.png" />

### Text input form with default value
<div class="codetabs">
    <div data-lang="scala" markdown="1">

{% highlight scala %}
%spark
println("Hello "+z.textbox("name", "sun")) 
{% endhighlight %}

    </div>
    <div data-lang="python" markdown="1">

{% highlight python %}
%pyspark
print("Hello "+z.textbox("name", "sun"))
{% endhighlight %}

    </div>
</div>
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/form_input_default_prog.png" />

### Select form
<div class="codetabs">
    <div data-lang="scala" markdown="1">

{% highlight scala %}
%spark
println("Hello "+z.select("day", Seq(("1","mon"),
                                    ("2","tue"),
                                    ("3","wed"),
                                    ("4","thurs"),
                                    ("5","fri"),
                                    ("6","sat"),
                                    ("7","sun"))))
{% endhighlight %}

    </div>
    <div data-lang="python" markdown="1">

{% highlight python %}
%pyspark
print("Hello "+z.select("day", [("1","mon"),
                                ("2","tue"),
                                ("3","wed"),
                                ("4","thurs"),
                                ("5","fri"),
                                ("6","sat"),
                                ("7","sun")]))
{% endhighlight %}

    </div>
</div>
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/form_select_prog.png" />

#### Checkbox form
<div class="codetabs">
    <div data-lang="scala" markdown="1">

{% highlight scala %}
%spark
val options = Seq(("apple","Apple"), ("banana","Banana"), ("orange","Orange"))
println("Hello "+z.checkbox("fruit", options).mkString(" and "))
{% endhighlight %}

    </div>
    <div data-lang="python" markdown="1">

{% highlight python %}
%pyspark
options = [("apple","Apple"), ("banana","Banana"), ("orange","Orange")]
print("Hello "+ " and ".join(z.checkbox("fruit", options, ["apple"])))
{% endhighlight %}

    </div>
</div>
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/form_checkbox_prog.png" />

## Creates Programmatically (scope: note)

The difference in the method names:

<table class="table-configuration">
  <tr>
    <th>Scope paragraph</th>
    <th>Scope note</th>
  </tr>
  <tr>
    <td>input (or textbox)</td>
    <td>noteTextbox</td>
  </tr>
  <tr>
    <td>select</td>
    <td>noteSelect</td>
  </tr>
  <tr>
    <td>checkbox</td>
    <td>noteCheckbox</td>
  </tr>
</table>

