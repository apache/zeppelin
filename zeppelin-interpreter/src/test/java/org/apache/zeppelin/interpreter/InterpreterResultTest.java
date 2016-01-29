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

package org.apache.zeppelin.interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;




public class InterpreterResultTest {

	@Test
	public void testTextType() {
	    
		InterpreterResult result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"this is a TEXT type"); 
    assertEquals("No magic",InterpreterResult.Type.TEXT, result.type());
		result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%this is a TEXT type");
		assertEquals("No magic",InterpreterResult.Type.TEXT, result.type());
		result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%\n");
		assertEquals("No magic",InterpreterResult.Type.TEXT, result.type());
	  }
	  @Test
	  public void testSimpleMagicType() {
		  InterpreterResult result = null;
		
		result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%table col1\tcol2\naaa\t123\n");
		assertEquals(InterpreterResult.Type.TABLE, result.type());
	  result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%table\ncol1\tcol2\naaa\t123\n");
	  assertEquals(InterpreterResult.Type.TABLE, result.type());
	  result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"some text before magic word %table col1\tcol2\naaa\t123\n");
	  assertEquals(InterpreterResult.Type.TABLE, result.type());
	  }

	  public void testComplexMagicType() {
		InterpreterResult result = null;
		
		result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"some text before %table col1\tcol2\naaa\t123\n"); 
	  assertEquals("some text before magic return magic",InterpreterResult.Type.TABLE, result.type());
	  result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%html  <h3> This is a hack </h3> %table\n col1\tcol2\naaa\t123\n");
	  assertEquals("magic A before magic B return magic A",InterpreterResult.Type.HTML, result.type());
	  result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"some text before magic word %table col1\tcol2\naaa\t123\n %html  <h3> This is a hack </h3>");
	  assertEquals("text & magic A before magic B return magic A" ,InterpreterResult.Type.TABLE, result.type());
	  result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%table col1\tcol2\naaa\t123\n %html  <h3> This is a hack </h3> %table col1\naaa\n123\n");
	  assertEquals("magic A, magic B, magic a' return magic A" ,InterpreterResult.Type.TABLE, result.type());

	  }

	  @Test
	  public void testSimpleMagicData() {
		
		InterpreterResult result = null;
		
		result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%table col1\tcol2\naaa\t123\n"); 
	    assertEquals("%table col1\tcol2\naaa\t123\n","col1\tcol2\naaa\t123\n", result.message());
	    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%table\ncol1\tcol2\naaa\t123\n"); 
	    assertEquals("%table\ncol1\tcol2\naaa\t123\n","col1\tcol2\naaa\t123\n", result.message());
	    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"some text before magic word %table col1\tcol2\naaa\t123\n"); 
	    assertEquals("some text before magic word %table col1\tcol2\naaa\t123\n","col1\tcol2\naaa\t123\n", result.message());
	  }
	  
	  @Test
	  public void testComplexMagicData() {
		
		InterpreterResult result = null;
		
		result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"some text before %table col1\tcol2\naaa\t123\n"); 
	    assertEquals("text before %table return %table","col1\tcol2\naaa\t123\n", result.message());
	    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%html  <h3> This is a hack </h3> %table\ncol1\tcol2\naaa\t123\n"); 
	    assertEquals("%html before %table return %html"," <h3> This is a hack </h3> %table\n" +
					"col1\tcol2\n" +
					"aaa\t123\n", result.message());
	    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"some text before magic word %table col1\tcol2\naaa\t123\n %html <h3> This is a hack </h3>"); 
	    assertEquals("text & %table befoe %html return %table","col1\tcol2\n" +
					"aaa\t123\n" +
					" %html <h3> This is a hack </h3>", result.message());
	    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%table col1\tcol2\naaa\t123\n %html  <h3> This is a hack </h3> %table col1\naaa\n123\n"); 
	    assertEquals("%table, %html before %table return first %table","col1\tcol2\n" +
					"aaa\t123\n" +
					" %html  <h3> This is a hack </h3> %table col1\n" +
					"aaa\n" +
					"123\n", result.message());
	    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%table col1\tcol2\naaa\t123\n %table col1\naaa\n123\n"); 
	    assertEquals("%table before %table return first %table","col1\tcol2\n" +
					"aaa\t123\n" +
					" %table col1\n" +
					"aaa\n" +
					"123\n", result.message());
	  }

	  @Test
	  public void testToString() {
			assertEquals("%html hello", new InterpreterResult(InterpreterResult.Code.SUCCESS, "%html hello").toString());
		}

}
