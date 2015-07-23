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
	    assertEquals(InterpreterResult.Type.TEXT, result.type());
	  }
	  @Test
	  public void testMagicType() {
		  InterpreterResult result = null;
		
		result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%table col1\tcol2\naaa\t123\n"); 
	    assertEquals(InterpreterResult.Type.TABLE, result.type());
	    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%table\n col1\tcol2\naaa\t123\n"); 
	    assertEquals(InterpreterResult.Type.TABLE, result.type());
	    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"some text before magic word %table col1\tcol2\naaa\t123\n"); 
	    assertEquals(InterpreterResult.Type.TABLE, result.type());
	  }
	  @Test
	  public void testMagicData() {
		
		InterpreterResult result = null;
		
		result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%table col1\tcol2\naaa\t123\n"); 
	    assertEquals("%table col1\tcol2\naaa\t123\n","col1\tcol2\naaa\t123\n", result.message());
	    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"%table\ncol1\tcol2\naaa\t123\n"); 
	    assertEquals("%table\ncol1\tcol2\naaa\t123\n","col1\tcol2\naaa\t123\n", result.message());
	    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,"some text before magic word %table col1\tcol2\naaa\t123\n"); 
	    assertEquals("some text before magic word %table col1\tcol2\naaa\t123\n","col1\tcol2\naaa\t123\n", result.message());
	  }

}
