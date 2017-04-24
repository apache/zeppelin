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

package org.apache.zeppelin.display;

import java.util.HashMap;
import java.util.Map;

import org.apache.zeppelin.display.ui.CheckBox;
import org.apache.zeppelin.display.ui.OptionInput.ParamOption;
import org.apache.zeppelin.display.ui.Select;
import org.apache.zeppelin.display.ui.TextBox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

public class InputTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testFormExtraction() {
		// textbox form
		String script = "${input_form=}";
		Map<String, Input> forms = Input.extractSimpleQueryForm(script);
		assertEquals(1, forms.size());
		Input form = forms.get("input_form");
		assertEquals("input_form", form.name);
		assertNull(form.displayName);
		assertEquals("", form.defaultValue);
		assertTrue(form instanceof TextBox);

		// textbox form with display name & default value
		script = "${input_form(Input Form)=xxx}";
		forms = Input.extractSimpleQueryForm(script);
		form = forms.get("input_form");
		assertEquals("xxx", form.defaultValue);
		assertTrue(form instanceof TextBox);

		// selection form
		script = "${select_form(Selection Form)=op1,op1|op2(Option 2)|op3}";
		form = Input.extractSimpleQueryForm(script).get("select_form");
		assertEquals("select_form", form.name);
		assertEquals("op1", form.defaultValue);
		assertTrue(form instanceof Select);
		assertArrayEquals(new ParamOption[]{new ParamOption("op1", null),
				new ParamOption("op2", "Option 2"), new ParamOption("op3", null)},
				((Select) form).getOptions());

		// checkbox form
		script = "${checkbox:checkbox_form=op1,op1|op2|op3}";
		form = Input.extractSimpleQueryForm(script).get("checkbox_form");
		assertEquals("checkbox_form", form.name);
		assertTrue(form instanceof CheckBox);

		assertArrayEquals(new Object[]{"op1"}, (Object[]) form.defaultValue);
		assertArrayEquals(new ParamOption[]{new ParamOption("op1", null),
				new ParamOption("op2", null), new ParamOption("op3", null)},
				((CheckBox) form).getOptions());

		// checkbox form with multiple default checks
		script = "${checkbox:checkbox_form(Checkbox Form)=op1|op3,op1(Option 1)|op2|op3}";
		form = Input.extractSimpleQueryForm(script).get("checkbox_form");
		assertEquals("checkbox_form", form.name);
		assertEquals("Checkbox Form", form.displayName);
		assertTrue(form instanceof CheckBox);
		assertArrayEquals(new Object[]{"op1", "op3"}, (Object[]) form.defaultValue);
		assertArrayEquals(new ParamOption[]{new ParamOption("op1", "Option 1"),
				new ParamOption("op2", null), new ParamOption("op3", null)},
				((CheckBox) form).getOptions());

		// checkbox form with no default check
		script = "${checkbox:checkbox_form(Checkbox Form)=,op1(Option 1)|op2(Option 2)|op3(Option 3)}";
		form = Input.extractSimpleQueryForm(script).get("checkbox_form");
		assertEquals("checkbox_form", form.name);
		assertEquals("Checkbox Form", form.displayName);
		assertTrue(form instanceof CheckBox);
		assertArrayEquals(new Object[]{}, (Object[]) form.defaultValue);
		assertArrayEquals(new ParamOption[]{new ParamOption("op1", "Option 1"),
				new ParamOption("op2", "Option 2"), new ParamOption("op3", "Option 3")},
				((CheckBox) form).getOptions());
	}


	@Test
	public void testFormSubstitution() {
		// test form substitution without new forms
		String script = "INPUT=${input_form=}SELECTED=${select_form(Selection Form)=,s_op1|s_op2|s_op3}\n" +
				"CHECKED=${checkbox:checkbox_form=c_op1|c_op2,c_op1|c_op2|c_op3}";
		Map<String, Object> params = new HashMap<>();
		params.put("input_form", "some_input");
		params.put("select_form", "s_op2");
		params.put("checkbox_form", new String[]{"c_op1", "c_op3"});
		String replaced = Input.getSimpleQuery(params, script);
		assertEquals("INPUT=some_inputSELECTED=s_op2\nCHECKED=c_op1,c_op3", replaced);

		// test form substitution with new forms
		script = "INPUT=${input_form=}SELECTED=${select_form(Selection Form)=,s_op1|s_op2|s_op3}\n" +
				"CHECKED=${checkbox:checkbox_form=c_op1|c_op2,c_op1|c_op2|c_op3}\n" +
				"NEW_CHECKED=${checkbox( and ):new_check=nc_a|nc_c,nc_a|nc_b|nc_c}";
		replaced = Input.getSimpleQuery(params, script);
		assertEquals("INPUT=some_inputSELECTED=s_op2\nCHECKED=c_op1,c_op3\n" +
				"NEW_CHECKED=nc_a and nc_c", replaced);

		// test form substitution with obsoleted values
		script = "INPUT=${input_form=}SELECTED=${select_form(Selection Form)=,s_op1|s_op2|s_op3}\n" +
				"CHECKED=${checkbox:checkbox_form=c_op1|c_op2,c_op1|c_op2|c_op3_new}\n" +
				"NEW_CHECKED=${checkbox( and ):new_check=nc_a|nc_c,nc_a|nc_b|nc_c}";
		replaced = Input.getSimpleQuery(params, script);
		assertEquals("INPUT=some_inputSELECTED=s_op2\nCHECKED=c_op1\n" +
				"NEW_CHECKED=nc_a and nc_c", replaced);
	}

}
