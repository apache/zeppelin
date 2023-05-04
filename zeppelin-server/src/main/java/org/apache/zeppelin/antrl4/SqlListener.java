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
package org.apache.zeppelin.antrl4;
 
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link org.apache.zeppelin.antrl4.SqlParser}.
 */
public interface SqlListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#program}.
	 * @param ctx the parse tree
	 */
	void enterProgram(org.apache.zeppelin.antrl4.SqlParser.ProgramContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#program}.
	 * @param ctx the parse tree
	 */
	void exitProgram(org.apache.zeppelin.antrl4.SqlParser.ProgramContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#block}.
	 * @param ctx the parse tree
	 */
	void enterBlock(org.apache.zeppelin.antrl4.SqlParser.BlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#block}.
	 * @param ctx the parse tree
	 */
	void exitBlock(org.apache.zeppelin.antrl4.SqlParser.BlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#begin_end_block}.
	 * @param ctx the parse tree
	 */
	void enterBegin_end_block(org.apache.zeppelin.antrl4.SqlParser.Begin_end_blockContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#begin_end_block}.
	 * @param ctx the parse tree
	 */
	void exitBegin_end_block(org.apache.zeppelin.antrl4.SqlParser.Begin_end_blockContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#single_block_stmt}.
	 * @param ctx the parse tree
	 */
	void enterSingle_block_stmt(org.apache.zeppelin.antrl4.SqlParser.Single_block_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#single_block_stmt}.
	 * @param ctx the parse tree
	 */
	void exitSingle_block_stmt(org.apache.zeppelin.antrl4.SqlParser.Single_block_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#block_end}.
	 * @param ctx the parse tree
	 */
	void enterBlock_end(org.apache.zeppelin.antrl4.SqlParser.Block_endContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#block_end}.
	 * @param ctx the parse tree
	 */
	void exitBlock_end(org.apache.zeppelin.antrl4.SqlParser.Block_endContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#proc_block}.
	 * @param ctx the parse tree
	 */
	void enterProc_block(org.apache.zeppelin.antrl4.SqlParser.Proc_blockContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#proc_block}.
	 * @param ctx the parse tree
	 */
	void exitProc_block(org.apache.zeppelin.antrl4.SqlParser.Proc_blockContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#stmt}.
	 * @param ctx the parse tree
	 */
	void enterStmt(org.apache.zeppelin.antrl4.SqlParser.StmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#stmt}.
	 * @param ctx the parse tree
	 */
	void exitStmt(org.apache.zeppelin.antrl4.SqlParser.StmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#semicolon_stmt}.
	 * @param ctx the parse tree
	 */
	void enterSemicolon_stmt(org.apache.zeppelin.antrl4.SqlParser.Semicolon_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#semicolon_stmt}.
	 * @param ctx the parse tree
	 */
	void exitSemicolon_stmt(org.apache.zeppelin.antrl4.SqlParser.Semicolon_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#exception_block}.
	 * @param ctx the parse tree
	 */
	void enterException_block(org.apache.zeppelin.antrl4.SqlParser.Exception_blockContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#exception_block}.
	 * @param ctx the parse tree
	 */
	void exitException_block(org.apache.zeppelin.antrl4.SqlParser.Exception_blockContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#exception_block_item}.
	 * @param ctx the parse tree
	 */
	void enterException_block_item(org.apache.zeppelin.antrl4.SqlParser.Exception_block_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#exception_block_item}.
	 * @param ctx the parse tree
	 */
	void exitException_block_item(org.apache.zeppelin.antrl4.SqlParser.Exception_block_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#null_stmt}.
	 * @param ctx the parse tree
	 */
	void enterNull_stmt(org.apache.zeppelin.antrl4.SqlParser.Null_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#null_stmt}.
	 * @param ctx the parse tree
	 */
	void exitNull_stmt(org.apache.zeppelin.antrl4.SqlParser.Null_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_stmt}.
	 * @param ctx the parse tree
	 */
	void enterExpr_stmt(org.apache.zeppelin.antrl4.SqlParser.Expr_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_stmt}.
	 * @param ctx the parse tree
	 */
	void exitExpr_stmt(org.apache.zeppelin.antrl4.SqlParser.Expr_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt}.
	 * @param ctx the parse tree
	 */
	void enterAssignment_stmt(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt}.
	 * @param ctx the parse tree
	 */
	void exitAssignment_stmt(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt_item}.
	 * @param ctx the parse tree
	 */
	void enterAssignment_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmt_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt_item}.
	 * @param ctx the parse tree
	 */
	void exitAssignment_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmt_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt_single_item}.
	 * @param ctx the parse tree
	 */
	void enterAssignment_stmt_single_item(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmt_single_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt_single_item}.
	 * @param ctx the parse tree
	 */
	void exitAssignment_stmt_single_item(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmt_single_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt_multiple_item}.
	 * @param ctx the parse tree
	 */
	void enterAssignment_stmt_multiple_item(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmt_multiple_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt_multiple_item}.
	 * @param ctx the parse tree
	 */
	void exitAssignment_stmt_multiple_item(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmt_multiple_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt_select_item}.
	 * @param ctx the parse tree
	 */
	void enterAssignment_stmt_select_item(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmt_select_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt_select_item}.
	 * @param ctx the parse tree
	 */
	void exitAssignment_stmt_select_item(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmt_select_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#allocate_cursor_stmt}.
	 * @param ctx the parse tree
	 */
	void enterAllocate_cursor_stmt(org.apache.zeppelin.antrl4.SqlParser.Allocate_cursor_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#allocate_cursor_stmt}.
	 * @param ctx the parse tree
	 */
	void exitAllocate_cursor_stmt(org.apache.zeppelin.antrl4.SqlParser.Allocate_cursor_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#associate_locator_stmt}.
	 * @param ctx the parse tree
	 */
	void enterAssociate_locator_stmt(org.apache.zeppelin.antrl4.SqlParser.Associate_locator_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#associate_locator_stmt}.
	 * @param ctx the parse tree
	 */
	void exitAssociate_locator_stmt(org.apache.zeppelin.antrl4.SqlParser.Associate_locator_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#begin_transaction_stmt}.
	 * @param ctx the parse tree
	 */
	void enterBegin_transaction_stmt(org.apache.zeppelin.antrl4.SqlParser.Begin_transaction_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#begin_transaction_stmt}.
	 * @param ctx the parse tree
	 */
	void exitBegin_transaction_stmt(org.apache.zeppelin.antrl4.SqlParser.Begin_transaction_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#break_stmt}.
	 * @param ctx the parse tree
	 */
	void enterBreak_stmt(org.apache.zeppelin.antrl4.SqlParser.Break_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#break_stmt}.
	 * @param ctx the parse tree
	 */
	void exitBreak_stmt(org.apache.zeppelin.antrl4.SqlParser.Break_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#call_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCall_stmt(org.apache.zeppelin.antrl4.SqlParser.Call_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#call_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCall_stmt(org.apache.zeppelin.antrl4.SqlParser.Call_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_stmt}.
	 * @param ctx the parse tree
	 */
	void enterDeclare_stmt(org.apache.zeppelin.antrl4.SqlParser.Declare_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_stmt}.
	 * @param ctx the parse tree
	 */
	void exitDeclare_stmt(org.apache.zeppelin.antrl4.SqlParser.Declare_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_block}.
	 * @param ctx the parse tree
	 */
	void enterDeclare_block(org.apache.zeppelin.antrl4.SqlParser.Declare_blockContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_block}.
	 * @param ctx the parse tree
	 */
	void exitDeclare_block(org.apache.zeppelin.antrl4.SqlParser.Declare_blockContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_block_inplace}.
	 * @param ctx the parse tree
	 */
	void enterDeclare_block_inplace(org.apache.zeppelin.antrl4.SqlParser.Declare_block_inplaceContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_block_inplace}.
	 * @param ctx the parse tree
	 */
	void exitDeclare_block_inplace(org.apache.zeppelin.antrl4.SqlParser.Declare_block_inplaceContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_stmt_item}.
	 * @param ctx the parse tree
	 */
	void enterDeclare_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Declare_stmt_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_stmt_item}.
	 * @param ctx the parse tree
	 */
	void exitDeclare_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Declare_stmt_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_var_item}.
	 * @param ctx the parse tree
	 */
	void enterDeclare_var_item(org.apache.zeppelin.antrl4.SqlParser.Declare_var_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_var_item}.
	 * @param ctx the parse tree
	 */
	void exitDeclare_var_item(org.apache.zeppelin.antrl4.SqlParser.Declare_var_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_condition_item}.
	 * @param ctx the parse tree
	 */
	void enterDeclare_condition_item(org.apache.zeppelin.antrl4.SqlParser.Declare_condition_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_condition_item}.
	 * @param ctx the parse tree
	 */
	void exitDeclare_condition_item(org.apache.zeppelin.antrl4.SqlParser.Declare_condition_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_cursor_item}.
	 * @param ctx the parse tree
	 */
	void enterDeclare_cursor_item(org.apache.zeppelin.antrl4.SqlParser.Declare_cursor_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_cursor_item}.
	 * @param ctx the parse tree
	 */
	void exitDeclare_cursor_item(org.apache.zeppelin.antrl4.SqlParser.Declare_cursor_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cursor_with_return}.
	 * @param ctx the parse tree
	 */
	void enterCursor_with_return(org.apache.zeppelin.antrl4.SqlParser.Cursor_with_returnContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cursor_with_return}.
	 * @param ctx the parse tree
	 */
	void exitCursor_with_return(org.apache.zeppelin.antrl4.SqlParser.Cursor_with_returnContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cursor_without_return}.
	 * @param ctx the parse tree
	 */
	void enterCursor_without_return(org.apache.zeppelin.antrl4.SqlParser.Cursor_without_returnContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cursor_without_return}.
	 * @param ctx the parse tree
	 */
	void exitCursor_without_return(org.apache.zeppelin.antrl4.SqlParser.Cursor_without_returnContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_handler_item}.
	 * @param ctx the parse tree
	 */
	void enterDeclare_handler_item(org.apache.zeppelin.antrl4.SqlParser.Declare_handler_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_handler_item}.
	 * @param ctx the parse tree
	 */
	void exitDeclare_handler_item(org.apache.zeppelin.antrl4.SqlParser.Declare_handler_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_temporary_table_item}.
	 * @param ctx the parse tree
	 */
	void enterDeclare_temporary_table_item(org.apache.zeppelin.antrl4.SqlParser.Declare_temporary_table_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_temporary_table_item}.
	 * @param ctx the parse tree
	 */
	void exitDeclare_temporary_table_item(org.apache.zeppelin.antrl4.SqlParser.Declare_temporary_table_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_table_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_table_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_local_temp_table_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCreate_local_temp_table_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_local_temp_table_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_local_temp_table_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCreate_local_temp_table_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_local_temp_table_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_definition}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_definition(org.apache.zeppelin.antrl4.SqlParser.Create_table_definitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_definition}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_definition(org.apache.zeppelin.antrl4.SqlParser.Create_table_definitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_columns}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_columns(org.apache.zeppelin.antrl4.SqlParser.Create_table_columnsContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_columns}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_columns(org.apache.zeppelin.antrl4.SqlParser.Create_table_columnsContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_columns_item}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_columns_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_columns_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_columns_item}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_columns_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_columns_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#column_comment}.
	 * @param ctx the parse tree
	 */
	void enterColumn_comment(org.apache.zeppelin.antrl4.SqlParser.Column_commentContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#column_comment}.
	 * @param ctx the parse tree
	 */
	void exitColumn_comment(org.apache.zeppelin.antrl4.SqlParser.Column_commentContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#column_name}.
	 * @param ctx the parse tree
	 */
	void enterColumn_name(org.apache.zeppelin.antrl4.SqlParser.Column_nameContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#column_name}.
	 * @param ctx the parse tree
	 */
	void exitColumn_name(org.apache.zeppelin.antrl4.SqlParser.Column_nameContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_column_inline_cons}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_column_inline_cons(org.apache.zeppelin.antrl4.SqlParser.Create_table_column_inline_consContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_column_inline_cons}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_column_inline_cons(org.apache.zeppelin.antrl4.SqlParser.Create_table_column_inline_consContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_column_cons}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_column_cons(org.apache.zeppelin.antrl4.SqlParser.Create_table_column_consContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_column_cons}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_column_cons(org.apache.zeppelin.antrl4.SqlParser.Create_table_column_consContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_fk_action}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_fk_action(org.apache.zeppelin.antrl4.SqlParser.Create_table_fk_actionContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_fk_action}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_fk_action(org.apache.zeppelin.antrl4.SqlParser.Create_table_fk_actionContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_preoptions}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_preoptions(org.apache.zeppelin.antrl4.SqlParser.Create_table_preoptionsContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_preoptions}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_preoptions(org.apache.zeppelin.antrl4.SqlParser.Create_table_preoptionsContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_preoptions_item}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_preoptions_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_preoptions_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_preoptions_item}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_preoptions_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_preoptions_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_preoptions_td_item}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_preoptions_td_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_preoptions_td_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_preoptions_td_item}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_preoptions_td_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_preoptions_td_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_options(org.apache.zeppelin.antrl4.SqlParser.Create_table_optionsContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_options(org.apache.zeppelin.antrl4.SqlParser.Create_table_optionsContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_item}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_options_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_item}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_options_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_ora_item}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_options_ora_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_ora_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_ora_item}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_options_ora_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_ora_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_db2_item}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_options_db2_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_db2_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_db2_item}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_options_db2_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_db2_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_td_item}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_options_td_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_td_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_td_item}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_options_td_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_td_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_hive_item}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_options_hive_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_hive_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_hive_item}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_options_hive_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_hive_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_hive_row_format}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_hive_row_format(org.apache.zeppelin.antrl4.SqlParser.Create_table_hive_row_formatContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_hive_row_format}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_hive_row_format(org.apache.zeppelin.antrl4.SqlParser.Create_table_hive_row_formatContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_hive_row_format_fields}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_hive_row_format_fields(org.apache.zeppelin.antrl4.SqlParser.Create_table_hive_row_format_fieldsContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_hive_row_format_fields}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_hive_row_format_fields(org.apache.zeppelin.antrl4.SqlParser.Create_table_hive_row_format_fieldsContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_mssql_item}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_options_mssql_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_mssql_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_mssql_item}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_options_mssql_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_mssql_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_mysql_item}.
	 * @param ctx the parse tree
	 */
	void enterCreate_table_options_mysql_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_mysql_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_mysql_item}.
	 * @param ctx the parse tree
	 */
	void exitCreate_table_options_mysql_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_mysql_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#alter_table_stmt}.
	 * @param ctx the parse tree
	 */
	void enterAlter_table_stmt(org.apache.zeppelin.antrl4.SqlParser.Alter_table_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#alter_table_stmt}.
	 * @param ctx the parse tree
	 */
	void exitAlter_table_stmt(org.apache.zeppelin.antrl4.SqlParser.Alter_table_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#alter_table_item}.
	 * @param ctx the parse tree
	 */
	void enterAlter_table_item(org.apache.zeppelin.antrl4.SqlParser.Alter_table_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#alter_table_item}.
	 * @param ctx the parse tree
	 */
	void exitAlter_table_item(org.apache.zeppelin.antrl4.SqlParser.Alter_table_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#alter_table_add_constraint}.
	 * @param ctx the parse tree
	 */
	void enterAlter_table_add_constraint(org.apache.zeppelin.antrl4.SqlParser.Alter_table_add_constraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#alter_table_add_constraint}.
	 * @param ctx the parse tree
	 */
	void exitAlter_table_add_constraint(org.apache.zeppelin.antrl4.SqlParser.Alter_table_add_constraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#alter_table_add_constraint_item}.
	 * @param ctx the parse tree
	 */
	void enterAlter_table_add_constraint_item(org.apache.zeppelin.antrl4.SqlParser.Alter_table_add_constraint_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#alter_table_add_constraint_item}.
	 * @param ctx the parse tree
	 */
	void exitAlter_table_add_constraint_item(org.apache.zeppelin.antrl4.SqlParser.Alter_table_add_constraint_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dtype}.
	 * @param ctx the parse tree
	 */
	void enterDtype(org.apache.zeppelin.antrl4.SqlParser.DtypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dtype}.
	 * @param ctx the parse tree
	 */
	void exitDtype(org.apache.zeppelin.antrl4.SqlParser.DtypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dtype_len}.
	 * @param ctx the parse tree
	 */
	void enterDtype_len(org.apache.zeppelin.antrl4.SqlParser.Dtype_lenContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dtype_len}.
	 * @param ctx the parse tree
	 */
	void exitDtype_len(org.apache.zeppelin.antrl4.SqlParser.Dtype_lenContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dtype_attr}.
	 * @param ctx the parse tree
	 */
	void enterDtype_attr(org.apache.zeppelin.antrl4.SqlParser.Dtype_attrContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dtype_attr}.
	 * @param ctx the parse tree
	 */
	void exitDtype_attr(org.apache.zeppelin.antrl4.SqlParser.Dtype_attrContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dtype_default}.
	 * @param ctx the parse tree
	 */
	void enterDtype_default(org.apache.zeppelin.antrl4.SqlParser.Dtype_defaultContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dtype_default}.
	 * @param ctx the parse tree
	 */
	void exitDtype_default(org.apache.zeppelin.antrl4.SqlParser.Dtype_defaultContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_database_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCreate_database_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_database_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_database_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCreate_database_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_database_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_database_option}.
	 * @param ctx the parse tree
	 */
	void enterCreate_database_option(org.apache.zeppelin.antrl4.SqlParser.Create_database_optionContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_database_option}.
	 * @param ctx the parse tree
	 */
	void exitCreate_database_option(org.apache.zeppelin.antrl4.SqlParser.Create_database_optionContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_function_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCreate_function_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_function_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_function_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCreate_function_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_function_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_function_return}.
	 * @param ctx the parse tree
	 */
	void enterCreate_function_return(org.apache.zeppelin.antrl4.SqlParser.Create_function_returnContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_function_return}.
	 * @param ctx the parse tree
	 */
	void exitCreate_function_return(org.apache.zeppelin.antrl4.SqlParser.Create_function_returnContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_package_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCreate_package_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_package_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_package_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCreate_package_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_package_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#package_spec}.
	 * @param ctx the parse tree
	 */
	void enterPackage_spec(org.apache.zeppelin.antrl4.SqlParser.Package_specContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#package_spec}.
	 * @param ctx the parse tree
	 */
	void exitPackage_spec(org.apache.zeppelin.antrl4.SqlParser.Package_specContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#package_spec_item}.
	 * @param ctx the parse tree
	 */
	void enterPackage_spec_item(org.apache.zeppelin.antrl4.SqlParser.Package_spec_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#package_spec_item}.
	 * @param ctx the parse tree
	 */
	void exitPackage_spec_item(org.apache.zeppelin.antrl4.SqlParser.Package_spec_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_package_body_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCreate_package_body_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_package_body_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_package_body_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCreate_package_body_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_package_body_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#package_body}.
	 * @param ctx the parse tree
	 */
	void enterPackage_body(org.apache.zeppelin.antrl4.SqlParser.Package_bodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#package_body}.
	 * @param ctx the parse tree
	 */
	void exitPackage_body(org.apache.zeppelin.antrl4.SqlParser.Package_bodyContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#package_body_item}.
	 * @param ctx the parse tree
	 */
	void enterPackage_body_item(org.apache.zeppelin.antrl4.SqlParser.Package_body_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#package_body_item}.
	 * @param ctx the parse tree
	 */
	void exitPackage_body_item(org.apache.zeppelin.antrl4.SqlParser.Package_body_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_procedure_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCreate_procedure_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_procedure_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_procedure_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCreate_procedure_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_procedure_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_routine_params}.
	 * @param ctx the parse tree
	 */
	void enterCreate_routine_params(org.apache.zeppelin.antrl4.SqlParser.Create_routine_paramsContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_routine_params}.
	 * @param ctx the parse tree
	 */
	void exitCreate_routine_params(org.apache.zeppelin.antrl4.SqlParser.Create_routine_paramsContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_routine_param_item}.
	 * @param ctx the parse tree
	 */
	void enterCreate_routine_param_item(org.apache.zeppelin.antrl4.SqlParser.Create_routine_param_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_routine_param_item}.
	 * @param ctx the parse tree
	 */
	void exitCreate_routine_param_item(org.apache.zeppelin.antrl4.SqlParser.Create_routine_param_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_routine_options}.
	 * @param ctx the parse tree
	 */
	void enterCreate_routine_options(org.apache.zeppelin.antrl4.SqlParser.Create_routine_optionsContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_routine_options}.
	 * @param ctx the parse tree
	 */
	void exitCreate_routine_options(org.apache.zeppelin.antrl4.SqlParser.Create_routine_optionsContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_routine_option}.
	 * @param ctx the parse tree
	 */
	void enterCreate_routine_option(org.apache.zeppelin.antrl4.SqlParser.Create_routine_optionContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_routine_option}.
	 * @param ctx the parse tree
	 */
	void exitCreate_routine_option(org.apache.zeppelin.antrl4.SqlParser.Create_routine_optionContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#drop_stmt}.
	 * @param ctx the parse tree
	 */
	void enterDrop_stmt(org.apache.zeppelin.antrl4.SqlParser.Drop_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#drop_stmt}.
	 * @param ctx the parse tree
	 */
	void exitDrop_stmt(org.apache.zeppelin.antrl4.SqlParser.Drop_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#end_transaction_stmt}.
	 * @param ctx the parse tree
	 */
	void enterEnd_transaction_stmt(org.apache.zeppelin.antrl4.SqlParser.End_transaction_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#end_transaction_stmt}.
	 * @param ctx the parse tree
	 */
	void exitEnd_transaction_stmt(org.apache.zeppelin.antrl4.SqlParser.End_transaction_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#exec_stmt}.
	 * @param ctx the parse tree
	 */
	void enterExec_stmt(org.apache.zeppelin.antrl4.SqlParser.Exec_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#exec_stmt}.
	 * @param ctx the parse tree
	 */
	void exitExec_stmt(org.apache.zeppelin.antrl4.SqlParser.Exec_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#if_stmt}.
	 * @param ctx the parse tree
	 */
	void enterIf_stmt(org.apache.zeppelin.antrl4.SqlParser.If_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#if_stmt}.
	 * @param ctx the parse tree
	 */
	void exitIf_stmt(org.apache.zeppelin.antrl4.SqlParser.If_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#if_plsql_stmt}.
	 * @param ctx the parse tree
	 */
	void enterIf_plsql_stmt(org.apache.zeppelin.antrl4.SqlParser.If_plsql_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#if_plsql_stmt}.
	 * @param ctx the parse tree
	 */
	void exitIf_plsql_stmt(org.apache.zeppelin.antrl4.SqlParser.If_plsql_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#if_tsql_stmt}.
	 * @param ctx the parse tree
	 */
	void enterIf_tsql_stmt(org.apache.zeppelin.antrl4.SqlParser.If_tsql_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#if_tsql_stmt}.
	 * @param ctx the parse tree
	 */
	void exitIf_tsql_stmt(org.apache.zeppelin.antrl4.SqlParser.If_tsql_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#if_bteq_stmt}.
	 * @param ctx the parse tree
	 */
	void enterIf_bteq_stmt(org.apache.zeppelin.antrl4.SqlParser.If_bteq_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#if_bteq_stmt}.
	 * @param ctx the parse tree
	 */
	void exitIf_bteq_stmt(org.apache.zeppelin.antrl4.SqlParser.If_bteq_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#elseif_block}.
	 * @param ctx the parse tree
	 */
	void enterElseif_block(org.apache.zeppelin.antrl4.SqlParser.Elseif_blockContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#elseif_block}.
	 * @param ctx the parse tree
	 */
	void exitElseif_block(org.apache.zeppelin.antrl4.SqlParser.Elseif_blockContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#else_block}.
	 * @param ctx the parse tree
	 */
	void enterElse_block(org.apache.zeppelin.antrl4.SqlParser.Else_blockContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#else_block}.
	 * @param ctx the parse tree
	 */
	void exitElse_block(org.apache.zeppelin.antrl4.SqlParser.Else_blockContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#include_stmt}.
	 * @param ctx the parse tree
	 */
	void enterInclude_stmt(org.apache.zeppelin.antrl4.SqlParser.Include_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#include_stmt}.
	 * @param ctx the parse tree
	 */
	void exitInclude_stmt(org.apache.zeppelin.antrl4.SqlParser.Include_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_stmt}.
	 * @param ctx the parse tree
	 */
	void enterInsert_stmt(org.apache.zeppelin.antrl4.SqlParser.Insert_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_stmt}.
	 * @param ctx the parse tree
	 */
	void exitInsert_stmt(org.apache.zeppelin.antrl4.SqlParser.Insert_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_stmt_cols}.
	 * @param ctx the parse tree
	 */
	void enterInsert_stmt_cols(org.apache.zeppelin.antrl4.SqlParser.Insert_stmt_colsContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_stmt_cols}.
	 * @param ctx the parse tree
	 */
	void exitInsert_stmt_cols(org.apache.zeppelin.antrl4.SqlParser.Insert_stmt_colsContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_stmt_rows}.
	 * @param ctx the parse tree
	 */
	void enterInsert_stmt_rows(org.apache.zeppelin.antrl4.SqlParser.Insert_stmt_rowsContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_stmt_rows}.
	 * @param ctx the parse tree
	 */
	void exitInsert_stmt_rows(org.apache.zeppelin.antrl4.SqlParser.Insert_stmt_rowsContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_stmt_row}.
	 * @param ctx the parse tree
	 */
	void enterInsert_stmt_row(org.apache.zeppelin.antrl4.SqlParser.Insert_stmt_rowContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_stmt_row}.
	 * @param ctx the parse tree
	 */
	void exitInsert_stmt_row(org.apache.zeppelin.antrl4.SqlParser.Insert_stmt_rowContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_directory_stmt}.
	 * @param ctx the parse tree
	 */
	void enterInsert_directory_stmt(org.apache.zeppelin.antrl4.SqlParser.Insert_directory_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_directory_stmt}.
	 * @param ctx the parse tree
	 */
	void exitInsert_directory_stmt(org.apache.zeppelin.antrl4.SqlParser.Insert_directory_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#exit_stmt}.
	 * @param ctx the parse tree
	 */
	void enterExit_stmt(org.apache.zeppelin.antrl4.SqlParser.Exit_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#exit_stmt}.
	 * @param ctx the parse tree
	 */
	void exitExit_stmt(org.apache.zeppelin.antrl4.SqlParser.Exit_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#get_diag_stmt}.
	 * @param ctx the parse tree
	 */
	void enterGet_diag_stmt(org.apache.zeppelin.antrl4.SqlParser.Get_diag_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#get_diag_stmt}.
	 * @param ctx the parse tree
	 */
	void exitGet_diag_stmt(org.apache.zeppelin.antrl4.SqlParser.Get_diag_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#get_diag_stmt_item}.
	 * @param ctx the parse tree
	 */
	void enterGet_diag_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Get_diag_stmt_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#get_diag_stmt_item}.
	 * @param ctx the parse tree
	 */
	void exitGet_diag_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Get_diag_stmt_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#get_diag_stmt_exception_item}.
	 * @param ctx the parse tree
	 */
	void enterGet_diag_stmt_exception_item(org.apache.zeppelin.antrl4.SqlParser.Get_diag_stmt_exception_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#get_diag_stmt_exception_item}.
	 * @param ctx the parse tree
	 */
	void exitGet_diag_stmt_exception_item(org.apache.zeppelin.antrl4.SqlParser.Get_diag_stmt_exception_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#get_diag_stmt_rowcount_item}.
	 * @param ctx the parse tree
	 */
	void enterGet_diag_stmt_rowcount_item(org.apache.zeppelin.antrl4.SqlParser.Get_diag_stmt_rowcount_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#get_diag_stmt_rowcount_item}.
	 * @param ctx the parse tree
	 */
	void exitGet_diag_stmt_rowcount_item(org.apache.zeppelin.antrl4.SqlParser.Get_diag_stmt_rowcount_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#grant_stmt}.
	 * @param ctx the parse tree
	 */
	void enterGrant_stmt(org.apache.zeppelin.antrl4.SqlParser.Grant_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#grant_stmt}.
	 * @param ctx the parse tree
	 */
	void exitGrant_stmt(org.apache.zeppelin.antrl4.SqlParser.Grant_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#grant_stmt_item}.
	 * @param ctx the parse tree
	 */
	void enterGrant_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Grant_stmt_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#grant_stmt_item}.
	 * @param ctx the parse tree
	 */
	void exitGrant_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Grant_stmt_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#leave_stmt}.
	 * @param ctx the parse tree
	 */
	void enterLeave_stmt(org.apache.zeppelin.antrl4.SqlParser.Leave_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#leave_stmt}.
	 * @param ctx the parse tree
	 */
	void exitLeave_stmt(org.apache.zeppelin.antrl4.SqlParser.Leave_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#map_object_stmt}.
	 * @param ctx the parse tree
	 */
	void enterMap_object_stmt(org.apache.zeppelin.antrl4.SqlParser.Map_object_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#map_object_stmt}.
	 * @param ctx the parse tree
	 */
	void exitMap_object_stmt(org.apache.zeppelin.antrl4.SqlParser.Map_object_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#open_stmt}.
	 * @param ctx the parse tree
	 */
	void enterOpen_stmt(org.apache.zeppelin.antrl4.SqlParser.Open_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#open_stmt}.
	 * @param ctx the parse tree
	 */
	void exitOpen_stmt(org.apache.zeppelin.antrl4.SqlParser.Open_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#fetch_stmt}.
	 * @param ctx the parse tree
	 */
	void enterFetch_stmt(org.apache.zeppelin.antrl4.SqlParser.Fetch_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#fetch_stmt}.
	 * @param ctx the parse tree
	 */
	void exitFetch_stmt(org.apache.zeppelin.antrl4.SqlParser.Fetch_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#collect_stats_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCollect_stats_stmt(org.apache.zeppelin.antrl4.SqlParser.Collect_stats_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#collect_stats_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCollect_stats_stmt(org.apache.zeppelin.antrl4.SqlParser.Collect_stats_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#collect_stats_clause}.
	 * @param ctx the parse tree
	 */
	void enterCollect_stats_clause(org.apache.zeppelin.antrl4.SqlParser.Collect_stats_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#collect_stats_clause}.
	 * @param ctx the parse tree
	 */
	void exitCollect_stats_clause(org.apache.zeppelin.antrl4.SqlParser.Collect_stats_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#close_stmt}.
	 * @param ctx the parse tree
	 */
	void enterClose_stmt(org.apache.zeppelin.antrl4.SqlParser.Close_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#close_stmt}.
	 * @param ctx the parse tree
	 */
	void exitClose_stmt(org.apache.zeppelin.antrl4.SqlParser.Close_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cmp_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCmp_stmt(org.apache.zeppelin.antrl4.SqlParser.Cmp_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cmp_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCmp_stmt(org.apache.zeppelin.antrl4.SqlParser.Cmp_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cmp_source}.
	 * @param ctx the parse tree
	 */
	void enterCmp_source(org.apache.zeppelin.antrl4.SqlParser.Cmp_sourceContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cmp_source}.
	 * @param ctx the parse tree
	 */
	void exitCmp_source(org.apache.zeppelin.antrl4.SqlParser.Cmp_sourceContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_from_local_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCopy_from_local_stmt(org.apache.zeppelin.antrl4.SqlParser.Copy_from_local_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_from_local_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCopy_from_local_stmt(org.apache.zeppelin.antrl4.SqlParser.Copy_from_local_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCopy_stmt(org.apache.zeppelin.antrl4.SqlParser.Copy_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCopy_stmt(org.apache.zeppelin.antrl4.SqlParser.Copy_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_source}.
	 * @param ctx the parse tree
	 */
	void enterCopy_source(org.apache.zeppelin.antrl4.SqlParser.Copy_sourceContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_source}.
	 * @param ctx the parse tree
	 */
	void exitCopy_source(org.apache.zeppelin.antrl4.SqlParser.Copy_sourceContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_target}.
	 * @param ctx the parse tree
	 */
	void enterCopy_target(org.apache.zeppelin.antrl4.SqlParser.Copy_targetContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_target}.
	 * @param ctx the parse tree
	 */
	void exitCopy_target(org.apache.zeppelin.antrl4.SqlParser.Copy_targetContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_option}.
	 * @param ctx the parse tree
	 */
	void enterCopy_option(org.apache.zeppelin.antrl4.SqlParser.Copy_optionContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_option}.
	 * @param ctx the parse tree
	 */
	void exitCopy_option(org.apache.zeppelin.antrl4.SqlParser.Copy_optionContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_file_option}.
	 * @param ctx the parse tree
	 */
	void enterCopy_file_option(org.apache.zeppelin.antrl4.SqlParser.Copy_file_optionContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_file_option}.
	 * @param ctx the parse tree
	 */
	void exitCopy_file_option(org.apache.zeppelin.antrl4.SqlParser.Copy_file_optionContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#commit_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCommit_stmt(org.apache.zeppelin.antrl4.SqlParser.Commit_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#commit_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCommit_stmt(org.apache.zeppelin.antrl4.SqlParser.Commit_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_index_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCreate_index_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_index_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_index_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCreate_index_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_index_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_index_col}.
	 * @param ctx the parse tree
	 */
	void enterCreate_index_col(org.apache.zeppelin.antrl4.SqlParser.Create_index_colContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_index_col}.
	 * @param ctx the parse tree
	 */
	void exitCreate_index_col(org.apache.zeppelin.antrl4.SqlParser.Create_index_colContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#index_storage_clause}.
	 * @param ctx the parse tree
	 */
	void enterIndex_storage_clause(org.apache.zeppelin.antrl4.SqlParser.Index_storage_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#index_storage_clause}.
	 * @param ctx the parse tree
	 */
	void exitIndex_storage_clause(org.apache.zeppelin.antrl4.SqlParser.Index_storage_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#index_mssql_storage_clause}.
	 * @param ctx the parse tree
	 */
	void enterIndex_mssql_storage_clause(org.apache.zeppelin.antrl4.SqlParser.Index_mssql_storage_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#index_mssql_storage_clause}.
	 * @param ctx the parse tree
	 */
	void exitIndex_mssql_storage_clause(org.apache.zeppelin.antrl4.SqlParser.Index_mssql_storage_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#print_stmt}.
	 * @param ctx the parse tree
	 */
	void enterPrint_stmt(org.apache.zeppelin.antrl4.SqlParser.Print_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#print_stmt}.
	 * @param ctx the parse tree
	 */
	void exitPrint_stmt(org.apache.zeppelin.antrl4.SqlParser.Print_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#quit_stmt}.
	 * @param ctx the parse tree
	 */
	void enterQuit_stmt(org.apache.zeppelin.antrl4.SqlParser.Quit_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#quit_stmt}.
	 * @param ctx the parse tree
	 */
	void exitQuit_stmt(org.apache.zeppelin.antrl4.SqlParser.Quit_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#raise_stmt}.
	 * @param ctx the parse tree
	 */
	void enterRaise_stmt(org.apache.zeppelin.antrl4.SqlParser.Raise_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#raise_stmt}.
	 * @param ctx the parse tree
	 */
	void exitRaise_stmt(org.apache.zeppelin.antrl4.SqlParser.Raise_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#resignal_stmt}.
	 * @param ctx the parse tree
	 */
	void enterResignal_stmt(org.apache.zeppelin.antrl4.SqlParser.Resignal_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#resignal_stmt}.
	 * @param ctx the parse tree
	 */
	void exitResignal_stmt(org.apache.zeppelin.antrl4.SqlParser.Resignal_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#return_stmt}.
	 * @param ctx the parse tree
	 */
	void enterReturn_stmt(org.apache.zeppelin.antrl4.SqlParser.Return_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#return_stmt}.
	 * @param ctx the parse tree
	 */
	void exitReturn_stmt(org.apache.zeppelin.antrl4.SqlParser.Return_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#rollback_stmt}.
	 * @param ctx the parse tree
	 */
	void enterRollback_stmt(org.apache.zeppelin.antrl4.SqlParser.Rollback_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#rollback_stmt}.
	 * @param ctx the parse tree
	 */
	void exitRollback_stmt(org.apache.zeppelin.antrl4.SqlParser.Rollback_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#set_session_option}.
	 * @param ctx the parse tree
	 */
	void enterSet_session_option(org.apache.zeppelin.antrl4.SqlParser.Set_session_optionContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#set_session_option}.
	 * @param ctx the parse tree
	 */
	void exitSet_session_option(org.apache.zeppelin.antrl4.SqlParser.Set_session_optionContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#set_current_schema_option}.
	 * @param ctx the parse tree
	 */
	void enterSet_current_schema_option(org.apache.zeppelin.antrl4.SqlParser.Set_current_schema_optionContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#set_current_schema_option}.
	 * @param ctx the parse tree
	 */
	void exitSet_current_schema_option(org.apache.zeppelin.antrl4.SqlParser.Set_current_schema_optionContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#set_mssql_session_option}.
	 * @param ctx the parse tree
	 */
	void enterSet_mssql_session_option(org.apache.zeppelin.antrl4.SqlParser.Set_mssql_session_optionContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#set_mssql_session_option}.
	 * @param ctx the parse tree
	 */
	void exitSet_mssql_session_option(org.apache.zeppelin.antrl4.SqlParser.Set_mssql_session_optionContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#set_teradata_session_option}.
	 * @param ctx the parse tree
	 */
	void enterSet_teradata_session_option(org.apache.zeppelin.antrl4.SqlParser.Set_teradata_session_optionContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#set_teradata_session_option}.
	 * @param ctx the parse tree
	 */
	void exitSet_teradata_session_option(org.apache.zeppelin.antrl4.SqlParser.Set_teradata_session_optionContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#signal_stmt}.
	 * @param ctx the parse tree
	 */
	void enterSignal_stmt(org.apache.zeppelin.antrl4.SqlParser.Signal_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#signal_stmt}.
	 * @param ctx the parse tree
	 */
	void exitSignal_stmt(org.apache.zeppelin.antrl4.SqlParser.Signal_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#summary_stmt}.
	 * @param ctx the parse tree
	 */
	void enterSummary_stmt(org.apache.zeppelin.antrl4.SqlParser.Summary_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#summary_stmt}.
	 * @param ctx the parse tree
	 */
	void exitSummary_stmt(org.apache.zeppelin.antrl4.SqlParser.Summary_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#truncate_stmt}.
	 * @param ctx the parse tree
	 */
	void enterTruncate_stmt(org.apache.zeppelin.antrl4.SqlParser.Truncate_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#truncate_stmt}.
	 * @param ctx the parse tree
	 */
	void exitTruncate_stmt(org.apache.zeppelin.antrl4.SqlParser.Truncate_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#use_stmt}.
	 * @param ctx the parse tree
	 */
	void enterUse_stmt(org.apache.zeppelin.antrl4.SqlParser.Use_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#use_stmt}.
	 * @param ctx the parse tree
	 */
	void exitUse_stmt(org.apache.zeppelin.antrl4.SqlParser.Use_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#values_into_stmt}.
	 * @param ctx the parse tree
	 */
	void enterValues_into_stmt(org.apache.zeppelin.antrl4.SqlParser.Values_into_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#values_into_stmt}.
	 * @param ctx the parse tree
	 */
	void exitValues_into_stmt(org.apache.zeppelin.antrl4.SqlParser.Values_into_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#while_stmt}.
	 * @param ctx the parse tree
	 */
	void enterWhile_stmt(org.apache.zeppelin.antrl4.SqlParser.While_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#while_stmt}.
	 * @param ctx the parse tree
	 */
	void exitWhile_stmt(org.apache.zeppelin.antrl4.SqlParser.While_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#for_cursor_stmt}.
	 * @param ctx the parse tree
	 */
	void enterFor_cursor_stmt(org.apache.zeppelin.antrl4.SqlParser.For_cursor_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#for_cursor_stmt}.
	 * @param ctx the parse tree
	 */
	void exitFor_cursor_stmt(org.apache.zeppelin.antrl4.SqlParser.For_cursor_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#for_range_stmt}.
	 * @param ctx the parse tree
	 */
	void enterFor_range_stmt(org.apache.zeppelin.antrl4.SqlParser.For_range_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#for_range_stmt}.
	 * @param ctx the parse tree
	 */
	void exitFor_range_stmt(org.apache.zeppelin.antrl4.SqlParser.For_range_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#label}.
	 * @param ctx the parse tree
	 */
	void enterLabel(org.apache.zeppelin.antrl4.SqlParser.LabelContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#label}.
	 * @param ctx the parse tree
	 */
	void exitLabel(org.apache.zeppelin.antrl4.SqlParser.LabelContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#using_clause}.
	 * @param ctx the parse tree
	 */
	void enterUsing_clause(org.apache.zeppelin.antrl4.SqlParser.Using_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#using_clause}.
	 * @param ctx the parse tree
	 */
	void exitUsing_clause(org.apache.zeppelin.antrl4.SqlParser.Using_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_stmt}.
	 * @param ctx the parse tree
	 */
	void enterSelect_stmt(org.apache.zeppelin.antrl4.SqlParser.Select_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_stmt}.
	 * @param ctx the parse tree
	 */
	void exitSelect_stmt(org.apache.zeppelin.antrl4.SqlParser.Select_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cte_select_stmt}.
	 * @param ctx the parse tree
	 */
	void enterCte_select_stmt(org.apache.zeppelin.antrl4.SqlParser.Cte_select_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cte_select_stmt}.
	 * @param ctx the parse tree
	 */
	void exitCte_select_stmt(org.apache.zeppelin.antrl4.SqlParser.Cte_select_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cte_select_stmt_item}.
	 * @param ctx the parse tree
	 */
	void enterCte_select_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Cte_select_stmt_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cte_select_stmt_item}.
	 * @param ctx the parse tree
	 */
	void exitCte_select_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Cte_select_stmt_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cte_select_cols}.
	 * @param ctx the parse tree
	 */
	void enterCte_select_cols(org.apache.zeppelin.antrl4.SqlParser.Cte_select_colsContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cte_select_cols}.
	 * @param ctx the parse tree
	 */
	void exitCte_select_cols(org.apache.zeppelin.antrl4.SqlParser.Cte_select_colsContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#fullselect_stmt}.
	 * @param ctx the parse tree
	 */
	void enterFullselect_stmt(org.apache.zeppelin.antrl4.SqlParser.Fullselect_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#fullselect_stmt}.
	 * @param ctx the parse tree
	 */
	void exitFullselect_stmt(org.apache.zeppelin.antrl4.SqlParser.Fullselect_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#fullselect_stmt_item}.
	 * @param ctx the parse tree
	 */
	void enterFullselect_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Fullselect_stmt_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#fullselect_stmt_item}.
	 * @param ctx the parse tree
	 */
	void exitFullselect_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Fullselect_stmt_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#fullselect_set_clause}.
	 * @param ctx the parse tree
	 */
	void enterFullselect_set_clause(org.apache.zeppelin.antrl4.SqlParser.Fullselect_set_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#fullselect_set_clause}.
	 * @param ctx the parse tree
	 */
	void exitFullselect_set_clause(org.apache.zeppelin.antrl4.SqlParser.Fullselect_set_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#subselect_stmt}.
	 * @param ctx the parse tree
	 */
	void enterSubselect_stmt(org.apache.zeppelin.antrl4.SqlParser.Subselect_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#subselect_stmt}.
	 * @param ctx the parse tree
	 */
	void exitSubselect_stmt(org.apache.zeppelin.antrl4.SqlParser.Subselect_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list}.
	 * @param ctx the parse tree
	 */
	void enterSelect_list(org.apache.zeppelin.antrl4.SqlParser.Select_listContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list}.
	 * @param ctx the parse tree
	 */
	void exitSelect_list(org.apache.zeppelin.antrl4.SqlParser.Select_listContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_set}.
	 * @param ctx the parse tree
	 */
	void enterSelect_list_set(org.apache.zeppelin.antrl4.SqlParser.Select_list_setContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_set}.
	 * @param ctx the parse tree
	 */
	void exitSelect_list_set(org.apache.zeppelin.antrl4.SqlParser.Select_list_setContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_limit}.
	 * @param ctx the parse tree
	 */
	void enterSelect_list_limit(org.apache.zeppelin.antrl4.SqlParser.Select_list_limitContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_limit}.
	 * @param ctx the parse tree
	 */
	void exitSelect_list_limit(org.apache.zeppelin.antrl4.SqlParser.Select_list_limitContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_item}.
	 * @param ctx the parse tree
	 */
	void enterSelect_list_item(org.apache.zeppelin.antrl4.SqlParser.Select_list_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_item}.
	 * @param ctx the parse tree
	 */
	void exitSelect_list_item(org.apache.zeppelin.antrl4.SqlParser.Select_list_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_alias}.
	 * @param ctx the parse tree
	 */
	void enterSelect_list_alias(org.apache.zeppelin.antrl4.SqlParser.Select_list_aliasContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_alias}.
	 * @param ctx the parse tree
	 */
	void exitSelect_list_alias(org.apache.zeppelin.antrl4.SqlParser.Select_list_aliasContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_asterisk}.
	 * @param ctx the parse tree
	 */
	void enterSelect_list_asterisk(org.apache.zeppelin.antrl4.SqlParser.Select_list_asteriskContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_asterisk}.
	 * @param ctx the parse tree
	 */
	void exitSelect_list_asterisk(org.apache.zeppelin.antrl4.SqlParser.Select_list_asteriskContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#into_clause}.
	 * @param ctx the parse tree
	 */
	void enterInto_clause(org.apache.zeppelin.antrl4.SqlParser.Into_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#into_clause}.
	 * @param ctx the parse tree
	 */
	void exitInto_clause(org.apache.zeppelin.antrl4.SqlParser.Into_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_clause}.
	 * @param ctx the parse tree
	 */
	void enterFrom_clause(org.apache.zeppelin.antrl4.SqlParser.From_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_clause}.
	 * @param ctx the parse tree
	 */
	void exitFrom_clause(org.apache.zeppelin.antrl4.SqlParser.From_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_table_clause}.
	 * @param ctx the parse tree
	 */
	void enterFrom_table_clause(org.apache.zeppelin.antrl4.SqlParser.From_table_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_table_clause}.
	 * @param ctx the parse tree
	 */
	void exitFrom_table_clause(org.apache.zeppelin.antrl4.SqlParser.From_table_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_table_name_clause}.
	 * @param ctx the parse tree
	 */
	void enterFrom_table_name_clause(org.apache.zeppelin.antrl4.SqlParser.From_table_name_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_table_name_clause}.
	 * @param ctx the parse tree
	 */
	void exitFrom_table_name_clause(org.apache.zeppelin.antrl4.SqlParser.From_table_name_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_subselect_clause}.
	 * @param ctx the parse tree
	 */
	void enterFrom_subselect_clause(org.apache.zeppelin.antrl4.SqlParser.From_subselect_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_subselect_clause}.
	 * @param ctx the parse tree
	 */
	void exitFrom_subselect_clause(org.apache.zeppelin.antrl4.SqlParser.From_subselect_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_join_clause}.
	 * @param ctx the parse tree
	 */
	void enterFrom_join_clause(org.apache.zeppelin.antrl4.SqlParser.From_join_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_join_clause}.
	 * @param ctx the parse tree
	 */
	void exitFrom_join_clause(org.apache.zeppelin.antrl4.SqlParser.From_join_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_join_type_clause}.
	 * @param ctx the parse tree
	 */
	void enterFrom_join_type_clause(org.apache.zeppelin.antrl4.SqlParser.From_join_type_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_join_type_clause}.
	 * @param ctx the parse tree
	 */
	void exitFrom_join_type_clause(org.apache.zeppelin.antrl4.SqlParser.From_join_type_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_table_values_clause}.
	 * @param ctx the parse tree
	 */
	void enterFrom_table_values_clause(org.apache.zeppelin.antrl4.SqlParser.From_table_values_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_table_values_clause}.
	 * @param ctx the parse tree
	 */
	void exitFrom_table_values_clause(org.apache.zeppelin.antrl4.SqlParser.From_table_values_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_table_values_row}.
	 * @param ctx the parse tree
	 */
	void enterFrom_table_values_row(org.apache.zeppelin.antrl4.SqlParser.From_table_values_rowContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_table_values_row}.
	 * @param ctx the parse tree
	 */
	void exitFrom_table_values_row(org.apache.zeppelin.antrl4.SqlParser.From_table_values_rowContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_alias_clause}.
	 * @param ctx the parse tree
	 */
	void enterFrom_alias_clause(org.apache.zeppelin.antrl4.SqlParser.From_alias_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_alias_clause}.
	 * @param ctx the parse tree
	 */
	void exitFrom_alias_clause(org.apache.zeppelin.antrl4.SqlParser.From_alias_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#table_name}.
	 * @param ctx the parse tree
	 */
	void enterTable_name(org.apache.zeppelin.antrl4.SqlParser.Table_nameContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#table_name}.
	 * @param ctx the parse tree
	 */
	void exitTable_name(org.apache.zeppelin.antrl4.SqlParser.Table_nameContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#where_clause}.
	 * @param ctx the parse tree
	 */
	void enterWhere_clause(org.apache.zeppelin.antrl4.SqlParser.Where_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#where_clause}.
	 * @param ctx the parse tree
	 */
	void exitWhere_clause(org.apache.zeppelin.antrl4.SqlParser.Where_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#group_by_clause}.
	 * @param ctx the parse tree
	 */
	void enterGroup_by_clause(org.apache.zeppelin.antrl4.SqlParser.Group_by_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#group_by_clause}.
	 * @param ctx the parse tree
	 */
	void exitGroup_by_clause(org.apache.zeppelin.antrl4.SqlParser.Group_by_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#having_clause}.
	 * @param ctx the parse tree
	 */
	void enterHaving_clause(org.apache.zeppelin.antrl4.SqlParser.Having_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#having_clause}.
	 * @param ctx the parse tree
	 */
	void exitHaving_clause(org.apache.zeppelin.antrl4.SqlParser.Having_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#qualify_clause}.
	 * @param ctx the parse tree
	 */
	void enterQualify_clause(org.apache.zeppelin.antrl4.SqlParser.Qualify_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#qualify_clause}.
	 * @param ctx the parse tree
	 */
	void exitQualify_clause(org.apache.zeppelin.antrl4.SqlParser.Qualify_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#order_by_clause}.
	 * @param ctx the parse tree
	 */
	void enterOrder_by_clause(org.apache.zeppelin.antrl4.SqlParser.Order_by_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#order_by_clause}.
	 * @param ctx the parse tree
	 */
	void exitOrder_by_clause(org.apache.zeppelin.antrl4.SqlParser.Order_by_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_options}.
	 * @param ctx the parse tree
	 */
	void enterSelect_options(org.apache.zeppelin.antrl4.SqlParser.Select_optionsContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_options}.
	 * @param ctx the parse tree
	 */
	void exitSelect_options(org.apache.zeppelin.antrl4.SqlParser.Select_optionsContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_options_item}.
	 * @param ctx the parse tree
	 */
	void enterSelect_options_item(org.apache.zeppelin.antrl4.SqlParser.Select_options_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_options_item}.
	 * @param ctx the parse tree
	 */
	void exitSelect_options_item(org.apache.zeppelin.antrl4.SqlParser.Select_options_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#update_stmt}.
	 * @param ctx the parse tree
	 */
	void enterUpdate_stmt(org.apache.zeppelin.antrl4.SqlParser.Update_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#update_stmt}.
	 * @param ctx the parse tree
	 */
	void exitUpdate_stmt(org.apache.zeppelin.antrl4.SqlParser.Update_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#update_assignment}.
	 * @param ctx the parse tree
	 */
	void enterUpdate_assignment(org.apache.zeppelin.antrl4.SqlParser.Update_assignmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#update_assignment}.
	 * @param ctx the parse tree
	 */
	void exitUpdate_assignment(org.apache.zeppelin.antrl4.SqlParser.Update_assignmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#update_table}.
	 * @param ctx the parse tree
	 */
	void enterUpdate_table(org.apache.zeppelin.antrl4.SqlParser.Update_tableContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#update_table}.
	 * @param ctx the parse tree
	 */
	void exitUpdate_table(org.apache.zeppelin.antrl4.SqlParser.Update_tableContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#update_upsert}.
	 * @param ctx the parse tree
	 */
	void enterUpdate_upsert(org.apache.zeppelin.antrl4.SqlParser.Update_upsertContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#update_upsert}.
	 * @param ctx the parse tree
	 */
	void exitUpdate_upsert(org.apache.zeppelin.antrl4.SqlParser.Update_upsertContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#merge_stmt}.
	 * @param ctx the parse tree
	 */
	void enterMerge_stmt(org.apache.zeppelin.antrl4.SqlParser.Merge_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#merge_stmt}.
	 * @param ctx the parse tree
	 */
	void exitMerge_stmt(org.apache.zeppelin.antrl4.SqlParser.Merge_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#merge_table}.
	 * @param ctx the parse tree
	 */
	void enterMerge_table(org.apache.zeppelin.antrl4.SqlParser.Merge_tableContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#merge_table}.
	 * @param ctx the parse tree
	 */
	void exitMerge_table(org.apache.zeppelin.antrl4.SqlParser.Merge_tableContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#merge_condition}.
	 * @param ctx the parse tree
	 */
	void enterMerge_condition(org.apache.zeppelin.antrl4.SqlParser.Merge_conditionContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#merge_condition}.
	 * @param ctx the parse tree
	 */
	void exitMerge_condition(org.apache.zeppelin.antrl4.SqlParser.Merge_conditionContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#merge_action}.
	 * @param ctx the parse tree
	 */
	void enterMerge_action(org.apache.zeppelin.antrl4.SqlParser.Merge_actionContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#merge_action}.
	 * @param ctx the parse tree
	 */
	void exitMerge_action(org.apache.zeppelin.antrl4.SqlParser.Merge_actionContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#delete_stmt}.
	 * @param ctx the parse tree
	 */
	void enterDelete_stmt(org.apache.zeppelin.antrl4.SqlParser.Delete_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#delete_stmt}.
	 * @param ctx the parse tree
	 */
	void exitDelete_stmt(org.apache.zeppelin.antrl4.SqlParser.Delete_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#delete_alias}.
	 * @param ctx the parse tree
	 */
	void enterDelete_alias(org.apache.zeppelin.antrl4.SqlParser.Delete_aliasContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#delete_alias}.
	 * @param ctx the parse tree
	 */
	void exitDelete_alias(org.apache.zeppelin.antrl4.SqlParser.Delete_aliasContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#describe_stmt}.
	 * @param ctx the parse tree
	 */
	void enterDescribe_stmt(org.apache.zeppelin.antrl4.SqlParser.Describe_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#describe_stmt}.
	 * @param ctx the parse tree
	 */
	void exitDescribe_stmt(org.apache.zeppelin.antrl4.SqlParser.Describe_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr}.
	 * @param ctx the parse tree
	 */
	void enterBool_expr(org.apache.zeppelin.antrl4.SqlParser.Bool_exprContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr}.
	 * @param ctx the parse tree
	 */
	void exitBool_expr(org.apache.zeppelin.antrl4.SqlParser.Bool_exprContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_atom}.
	 * @param ctx the parse tree
	 */
	void enterBool_expr_atom(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_atomContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_atom}.
	 * @param ctx the parse tree
	 */
	void exitBool_expr_atom(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_atomContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_unary}.
	 * @param ctx the parse tree
	 */
	void enterBool_expr_unary(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_unaryContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_unary}.
	 * @param ctx the parse tree
	 */
	void exitBool_expr_unary(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_unaryContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_single_in}.
	 * @param ctx the parse tree
	 */
	void enterBool_expr_single_in(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_single_inContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_single_in}.
	 * @param ctx the parse tree
	 */
	void exitBool_expr_single_in(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_single_inContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_multi_in}.
	 * @param ctx the parse tree
	 */
	void enterBool_expr_multi_in(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_multi_inContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_multi_in}.
	 * @param ctx the parse tree
	 */
	void exitBool_expr_multi_in(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_multi_inContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_binary}.
	 * @param ctx the parse tree
	 */
	void enterBool_expr_binary(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_binaryContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_binary}.
	 * @param ctx the parse tree
	 */
	void exitBool_expr_binary(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_binaryContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_logical_operator}.
	 * @param ctx the parse tree
	 */
	void enterBool_expr_logical_operator(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_logical_operatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_logical_operator}.
	 * @param ctx the parse tree
	 */
	void exitBool_expr_logical_operator(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_logical_operatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_binary_operator}.
	 * @param ctx the parse tree
	 */
	void enterBool_expr_binary_operator(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_binary_operatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_binary_operator}.
	 * @param ctx the parse tree
	 */
	void exitBool_expr_binary_operator(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_binary_operatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterExpr(org.apache.zeppelin.antrl4.SqlParser.ExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitExpr(org.apache.zeppelin.antrl4.SqlParser.ExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_atom}.
	 * @param ctx the parse tree
	 */
	void enterExpr_atom(org.apache.zeppelin.antrl4.SqlParser.Expr_atomContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_atom}.
	 * @param ctx the parse tree
	 */
	void exitExpr_atom(org.apache.zeppelin.antrl4.SqlParser.Expr_atomContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_interval}.
	 * @param ctx the parse tree
	 */
	void enterExpr_interval(org.apache.zeppelin.antrl4.SqlParser.Expr_intervalContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_interval}.
	 * @param ctx the parse tree
	 */
	void exitExpr_interval(org.apache.zeppelin.antrl4.SqlParser.Expr_intervalContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#interval_item}.
	 * @param ctx the parse tree
	 */
	void enterInterval_item(org.apache.zeppelin.antrl4.SqlParser.Interval_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#interval_item}.
	 * @param ctx the parse tree
	 */
	void exitInterval_item(org.apache.zeppelin.antrl4.SqlParser.Interval_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_concat}.
	 * @param ctx the parse tree
	 */
	void enterExpr_concat(org.apache.zeppelin.antrl4.SqlParser.Expr_concatContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_concat}.
	 * @param ctx the parse tree
	 */
	void exitExpr_concat(org.apache.zeppelin.antrl4.SqlParser.Expr_concatContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_concat_item}.
	 * @param ctx the parse tree
	 */
	void enterExpr_concat_item(org.apache.zeppelin.antrl4.SqlParser.Expr_concat_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_concat_item}.
	 * @param ctx the parse tree
	 */
	void exitExpr_concat_item(org.apache.zeppelin.antrl4.SqlParser.Expr_concat_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_case}.
	 * @param ctx the parse tree
	 */
	void enterExpr_case(org.apache.zeppelin.antrl4.SqlParser.Expr_caseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_case}.
	 * @param ctx the parse tree
	 */
	void exitExpr_case(org.apache.zeppelin.antrl4.SqlParser.Expr_caseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_case_simple}.
	 * @param ctx the parse tree
	 */
	void enterExpr_case_simple(org.apache.zeppelin.antrl4.SqlParser.Expr_case_simpleContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_case_simple}.
	 * @param ctx the parse tree
	 */
	void exitExpr_case_simple(org.apache.zeppelin.antrl4.SqlParser.Expr_case_simpleContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_case_searched}.
	 * @param ctx the parse tree
	 */
	void enterExpr_case_searched(org.apache.zeppelin.antrl4.SqlParser.Expr_case_searchedContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_case_searched}.
	 * @param ctx the parse tree
	 */
	void exitExpr_case_searched(org.apache.zeppelin.antrl4.SqlParser.Expr_case_searchedContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_cursor_attribute}.
	 * @param ctx the parse tree
	 */
	void enterExpr_cursor_attribute(org.apache.zeppelin.antrl4.SqlParser.Expr_cursor_attributeContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_cursor_attribute}.
	 * @param ctx the parse tree
	 */
	void exitExpr_cursor_attribute(org.apache.zeppelin.antrl4.SqlParser.Expr_cursor_attributeContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_agg_window_func}.
	 * @param ctx the parse tree
	 */
	void enterExpr_agg_window_func(org.apache.zeppelin.antrl4.SqlParser.Expr_agg_window_funcContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_agg_window_func}.
	 * @param ctx the parse tree
	 */
	void exitExpr_agg_window_func(org.apache.zeppelin.antrl4.SqlParser.Expr_agg_window_funcContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func_all_distinct}.
	 * @param ctx the parse tree
	 */
	void enterExpr_func_all_distinct(org.apache.zeppelin.antrl4.SqlParser.Expr_func_all_distinctContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func_all_distinct}.
	 * @param ctx the parse tree
	 */
	void exitExpr_func_all_distinct(org.apache.zeppelin.antrl4.SqlParser.Expr_func_all_distinctContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func_over_clause}.
	 * @param ctx the parse tree
	 */
	void enterExpr_func_over_clause(org.apache.zeppelin.antrl4.SqlParser.Expr_func_over_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func_over_clause}.
	 * @param ctx the parse tree
	 */
	void exitExpr_func_over_clause(org.apache.zeppelin.antrl4.SqlParser.Expr_func_over_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func_partition_by_clause}.
	 * @param ctx the parse tree
	 */
	void enterExpr_func_partition_by_clause(org.apache.zeppelin.antrl4.SqlParser.Expr_func_partition_by_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func_partition_by_clause}.
	 * @param ctx the parse tree
	 */
	void exitExpr_func_partition_by_clause(org.apache.zeppelin.antrl4.SqlParser.Expr_func_partition_by_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_spec_func}.
	 * @param ctx the parse tree
	 */
	void enterExpr_spec_func(org.apache.zeppelin.antrl4.SqlParser.Expr_spec_funcContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_spec_func}.
	 * @param ctx the parse tree
	 */
	void exitExpr_spec_func(org.apache.zeppelin.antrl4.SqlParser.Expr_spec_funcContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func}.
	 * @param ctx the parse tree
	 */
	void enterExpr_func(org.apache.zeppelin.antrl4.SqlParser.Expr_funcContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func}.
	 * @param ctx the parse tree
	 */
	void exitExpr_func(org.apache.zeppelin.antrl4.SqlParser.Expr_funcContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func_params}.
	 * @param ctx the parse tree
	 */
	void enterExpr_func_params(org.apache.zeppelin.antrl4.SqlParser.Expr_func_paramsContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func_params}.
	 * @param ctx the parse tree
	 */
	void exitExpr_func_params(org.apache.zeppelin.antrl4.SqlParser.Expr_func_paramsContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#func_param}.
	 * @param ctx the parse tree
	 */
	void enterFunc_param(org.apache.zeppelin.antrl4.SqlParser.Func_paramContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#func_param}.
	 * @param ctx the parse tree
	 */
	void exitFunc_param(org.apache.zeppelin.antrl4.SqlParser.Func_paramContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_select}.
	 * @param ctx the parse tree
	 */
	void enterExpr_select(org.apache.zeppelin.antrl4.SqlParser.Expr_selectContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_select}.
	 * @param ctx the parse tree
	 */
	void exitExpr_select(org.apache.zeppelin.antrl4.SqlParser.Expr_selectContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_file}.
	 * @param ctx the parse tree
	 */
	void enterExpr_file(org.apache.zeppelin.antrl4.SqlParser.Expr_fileContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_file}.
	 * @param ctx the parse tree
	 */
	void exitExpr_file(org.apache.zeppelin.antrl4.SqlParser.Expr_fileContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#hive}.
	 * @param ctx the parse tree
	 */
	void enterHive(org.apache.zeppelin.antrl4.SqlParser.HiveContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#hive}.
	 * @param ctx the parse tree
	 */
	void exitHive(org.apache.zeppelin.antrl4.SqlParser.HiveContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#hive_item}.
	 * @param ctx the parse tree
	 */
	void enterHive_item(org.apache.zeppelin.antrl4.SqlParser.Hive_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#hive_item}.
	 * @param ctx the parse tree
	 */
	void exitHive_item(org.apache.zeppelin.antrl4.SqlParser.Hive_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#host}.
	 * @param ctx the parse tree
	 */
	void enterHost(org.apache.zeppelin.antrl4.SqlParser.HostContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#host}.
	 * @param ctx the parse tree
	 */
	void exitHost(org.apache.zeppelin.antrl4.SqlParser.HostContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#host_cmd}.
	 * @param ctx the parse tree
	 */
	void enterHost_cmd(org.apache.zeppelin.antrl4.SqlParser.Host_cmdContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#host_cmd}.
	 * @param ctx the parse tree
	 */
	void exitHost_cmd(org.apache.zeppelin.antrl4.SqlParser.Host_cmdContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#host_stmt}.
	 * @param ctx the parse tree
	 */
	void enterHost_stmt(org.apache.zeppelin.antrl4.SqlParser.Host_stmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#host_stmt}.
	 * @param ctx the parse tree
	 */
	void exitHost_stmt(org.apache.zeppelin.antrl4.SqlParser.Host_stmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#file_name}.
	 * @param ctx the parse tree
	 */
	void enterFile_name(org.apache.zeppelin.antrl4.SqlParser.File_nameContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#file_name}.
	 * @param ctx the parse tree
	 */
	void exitFile_name(org.apache.zeppelin.antrl4.SqlParser.File_nameContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#date_literal}.
	 * @param ctx the parse tree
	 */
	void enterDate_literal(org.apache.zeppelin.antrl4.SqlParser.Date_literalContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#date_literal}.
	 * @param ctx the parse tree
	 */
	void exitDate_literal(org.apache.zeppelin.antrl4.SqlParser.Date_literalContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#timestamp_literal}.
	 * @param ctx the parse tree
	 */
	void enterTimestamp_literal(org.apache.zeppelin.antrl4.SqlParser.Timestamp_literalContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#timestamp_literal}.
	 * @param ctx the parse tree
	 */
	void exitTimestamp_literal(org.apache.zeppelin.antrl4.SqlParser.Timestamp_literalContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#ident}.
	 * @param ctx the parse tree
	 */
	void enterIdent(org.apache.zeppelin.antrl4.SqlParser.IdentContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#ident}.
	 * @param ctx the parse tree
	 */
	void exitIdent(org.apache.zeppelin.antrl4.SqlParser.IdentContext ctx);
	/**
	 * Enter a parse tree produced by the {@code single_quotedString}
	 * labeled alternative in {@link org.apache.zeppelin.antrl4.SqlParser#string}.
	 * @param ctx the parse tree
	 */
	void enterSingle_quotedString(org.apache.zeppelin.antrl4.SqlParser.Single_quotedStringContext ctx);
	/**
	 * Exit a parse tree produced by the {@code single_quotedString}
	 * labeled alternative in {@link org.apache.zeppelin.antrl4.SqlParser#string}.
	 * @param ctx the parse tree
	 */
	void exitSingle_quotedString(org.apache.zeppelin.antrl4.SqlParser.Single_quotedStringContext ctx);
	/**
	 * Enter a parse tree produced by the {@code double_quotedString}
	 * labeled alternative in {@link org.apache.zeppelin.antrl4.SqlParser#string}.
	 * @param ctx the parse tree
	 */
	void enterDouble_quotedString(org.apache.zeppelin.antrl4.SqlParser.Double_quotedStringContext ctx);
	/**
	 * Exit a parse tree produced by the {@code double_quotedString}
	 * labeled alternative in {@link org.apache.zeppelin.antrl4.SqlParser#string}.
	 * @param ctx the parse tree
	 */
	void exitDouble_quotedString(org.apache.zeppelin.antrl4.SqlParser.Double_quotedStringContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#int_number}.
	 * @param ctx the parse tree
	 */
	void enterInt_number(org.apache.zeppelin.antrl4.SqlParser.Int_numberContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#int_number}.
	 * @param ctx the parse tree
	 */
	void exitInt_number(org.apache.zeppelin.antrl4.SqlParser.Int_numberContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dec_number}.
	 * @param ctx the parse tree
	 */
	void enterDec_number(org.apache.zeppelin.antrl4.SqlParser.Dec_numberContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dec_number}.
	 * @param ctx the parse tree
	 */
	void exitDec_number(org.apache.zeppelin.antrl4.SqlParser.Dec_numberContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_literal}.
	 * @param ctx the parse tree
	 */
	void enterBool_literal(org.apache.zeppelin.antrl4.SqlParser.Bool_literalContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_literal}.
	 * @param ctx the parse tree
	 */
	void exitBool_literal(org.apache.zeppelin.antrl4.SqlParser.Bool_literalContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#null_const}.
	 * @param ctx the parse tree
	 */
	void enterNull_const(org.apache.zeppelin.antrl4.SqlParser.Null_constContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#null_const}.
	 * @param ctx the parse tree
	 */
	void exitNull_const(org.apache.zeppelin.antrl4.SqlParser.Null_constContext ctx);
	/**
	 * Enter a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#non_reserved_words}.
	 * @param ctx the parse tree
	 */
	void enterNon_reserved_words(org.apache.zeppelin.antrl4.SqlParser.Non_reserved_wordsContext ctx);
	/**
	 * Exit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#non_reserved_words}.
	 * @param ctx the parse tree
	 */
	void exitNon_reserved_words(org.apache.zeppelin.antrl4.SqlParser.Non_reserved_wordsContext ctx);
}