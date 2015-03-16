---
layout: page
title: "Dynamic Form"
description: ""
group: manual
---
{% include JB/setup %}


## Dynamic Form

Zeppelin dynamically creates input forms. Depend on language backend, there're two different way to create dynamic form.
Custom lanaugage backend can select which type of form creation he want to use.

<br />
### Using form Templates

This mode creates form using simple template language. It's simple and easy to use. For example Markdown, Shell, SparkSql language backend uses it.

<br />
#### Text input form

To create text input form, use _${formName}_ templates.

for example

<img src="../assets/themes/zeppelin/img/screenshots/form_input.png" />


Also you can provide default value, using _${formName=defaultValue}_.

<img src="../assets/themes/zeppelin/img/screenshots/form_input_default.png" />


<br />
#### Select form

To create select form, use _${formName=defaultValue,option1|option2...}_

for example

<img src="../assets/themes/zeppelin/img/screenshots/form_select.png" />

Also you can separate option's display name and value, using _${formName=defaultValue,option1(DisplayName)|option2(DisplayName)...}_

<img src="../assets/themes/zeppelin/img/screenshots/form_select_displayname.png" />

<br />
### Creates Programatically

Some language backend use programtic way to create form. for example [ZeppelinContext](./interpreter/spark.html#zeppelincontext) provides form creation API

Here're some examples.

Text input form

<img src="../assets/themes/zeppelin/img/screenshots/form_input_prog.png" />

Text input form with default value

<img src="../assets/themes/zeppelin/img/screenshots/form_input_default_prog.png" />

Select form

<img src="../assets/themes/zeppelin/img/screenshots/form_select_prog.png" />