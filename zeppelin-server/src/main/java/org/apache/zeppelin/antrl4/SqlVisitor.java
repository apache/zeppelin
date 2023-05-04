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
 
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link org.apache.zeppelin.antrl4.SqlParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface SqlVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#program}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProgram(org.apache.zeppelin.antrl4.SqlParser.ProgramContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlock(org.apache.zeppelin.antrl4.SqlParser.BlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#begin_end_block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBegin_end_block(org.apache.zeppelin.antrl4.SqlParser.Begin_end_blockContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#single_block_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSingle_block_stmt(org.apache.zeppelin.antrl4.SqlParser.Single_block_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#block_end}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlock_end(org.apache.zeppelin.antrl4.SqlParser.Block_endContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#proc_block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProc_block(org.apache.zeppelin.antrl4.SqlParser.Proc_blockContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStmt(org.apache.zeppelin.antrl4.SqlParser.StmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#semicolon_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSemicolon_stmt(org.apache.zeppelin.antrl4.SqlParser.Semicolon_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#exception_block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitException_block(org.apache.zeppelin.antrl4.SqlParser.Exception_blockContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#exception_block_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitException_block_item(org.apache.zeppelin.antrl4.SqlParser.Exception_block_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#null_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNull_stmt(org.apache.zeppelin.antrl4.SqlParser.Null_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_stmt(org.apache.zeppelin.antrl4.SqlParser.Expr_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignment_stmt(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignment_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmt_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt_single_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignment_stmt_single_item(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmt_single_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt_multiple_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignment_stmt_multiple_item(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmt_multiple_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#assignment_stmt_select_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignment_stmt_select_item(org.apache.zeppelin.antrl4.SqlParser.Assignment_stmt_select_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#allocate_cursor_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAllocate_cursor_stmt(org.apache.zeppelin.antrl4.SqlParser.Allocate_cursor_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#associate_locator_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssociate_locator_stmt(org.apache.zeppelin.antrl4.SqlParser.Associate_locator_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#begin_transaction_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBegin_transaction_stmt(org.apache.zeppelin.antrl4.SqlParser.Begin_transaction_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#break_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBreak_stmt(org.apache.zeppelin.antrl4.SqlParser.Break_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#call_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCall_stmt(org.apache.zeppelin.antrl4.SqlParser.Call_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDeclare_stmt(org.apache.zeppelin.antrl4.SqlParser.Declare_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDeclare_block(org.apache.zeppelin.antrl4.SqlParser.Declare_blockContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_block_inplace}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDeclare_block_inplace(org.apache.zeppelin.antrl4.SqlParser.Declare_block_inplaceContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_stmt_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDeclare_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Declare_stmt_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_var_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDeclare_var_item(org.apache.zeppelin.antrl4.SqlParser.Declare_var_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_condition_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDeclare_condition_item(org.apache.zeppelin.antrl4.SqlParser.Declare_condition_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_cursor_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDeclare_cursor_item(org.apache.zeppelin.antrl4.SqlParser.Declare_cursor_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cursor_with_return}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCursor_with_return(org.apache.zeppelin.antrl4.SqlParser.Cursor_with_returnContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cursor_without_return}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCursor_without_return(org.apache.zeppelin.antrl4.SqlParser.Cursor_without_returnContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_handler_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDeclare_handler_item(org.apache.zeppelin.antrl4.SqlParser.Declare_handler_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#declare_temporary_table_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDeclare_temporary_table_item(org.apache.zeppelin.antrl4.SqlParser.Declare_temporary_table_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_table_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_local_temp_table_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_local_temp_table_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_local_temp_table_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_definition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_definition(org.apache.zeppelin.antrl4.SqlParser.Create_table_definitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_columns}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_columns(org.apache.zeppelin.antrl4.SqlParser.Create_table_columnsContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_columns_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_columns_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_columns_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#column_comment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitColumn_comment(org.apache.zeppelin.antrl4.SqlParser.Column_commentContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#column_name}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitColumn_name(org.apache.zeppelin.antrl4.SqlParser.Column_nameContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_column_inline_cons}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_column_inline_cons(org.apache.zeppelin.antrl4.SqlParser.Create_table_column_inline_consContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_column_cons}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_column_cons(org.apache.zeppelin.antrl4.SqlParser.Create_table_column_consContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_fk_action}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_fk_action(org.apache.zeppelin.antrl4.SqlParser.Create_table_fk_actionContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_preoptions}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_preoptions(org.apache.zeppelin.antrl4.SqlParser.Create_table_preoptionsContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_preoptions_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_preoptions_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_preoptions_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_preoptions_td_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_preoptions_td_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_preoptions_td_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_options(org.apache.zeppelin.antrl4.SqlParser.Create_table_optionsContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_options_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_ora_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_options_ora_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_ora_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_db2_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_options_db2_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_db2_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_td_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_options_td_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_td_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_hive_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_options_hive_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_hive_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_hive_row_format}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_hive_row_format(org.apache.zeppelin.antrl4.SqlParser.Create_table_hive_row_formatContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_hive_row_format_fields}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_hive_row_format_fields(org.apache.zeppelin.antrl4.SqlParser.Create_table_hive_row_format_fieldsContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_mssql_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_options_mssql_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_mssql_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_table_options_mysql_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_table_options_mysql_item(org.apache.zeppelin.antrl4.SqlParser.Create_table_options_mysql_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#alter_table_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAlter_table_stmt(org.apache.zeppelin.antrl4.SqlParser.Alter_table_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#alter_table_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAlter_table_item(org.apache.zeppelin.antrl4.SqlParser.Alter_table_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#alter_table_add_constraint}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAlter_table_add_constraint(org.apache.zeppelin.antrl4.SqlParser.Alter_table_add_constraintContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#alter_table_add_constraint_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAlter_table_add_constraint_item(org.apache.zeppelin.antrl4.SqlParser.Alter_table_add_constraint_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dtype}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDtype(org.apache.zeppelin.antrl4.SqlParser.DtypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dtype_len}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDtype_len(org.apache.zeppelin.antrl4.SqlParser.Dtype_lenContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dtype_attr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDtype_attr(org.apache.zeppelin.antrl4.SqlParser.Dtype_attrContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dtype_default}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDtype_default(org.apache.zeppelin.antrl4.SqlParser.Dtype_defaultContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_database_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_database_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_database_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_database_option}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_database_option(org.apache.zeppelin.antrl4.SqlParser.Create_database_optionContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_function_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_function_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_function_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_function_return}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_function_return(org.apache.zeppelin.antrl4.SqlParser.Create_function_returnContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_package_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_package_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_package_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#package_spec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPackage_spec(org.apache.zeppelin.antrl4.SqlParser.Package_specContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#package_spec_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPackage_spec_item(org.apache.zeppelin.antrl4.SqlParser.Package_spec_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_package_body_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_package_body_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_package_body_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#package_body}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPackage_body(org.apache.zeppelin.antrl4.SqlParser.Package_bodyContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#package_body_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPackage_body_item(org.apache.zeppelin.antrl4.SqlParser.Package_body_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_procedure_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_procedure_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_procedure_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_routine_params}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_routine_params(org.apache.zeppelin.antrl4.SqlParser.Create_routine_paramsContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_routine_param_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_routine_param_item(org.apache.zeppelin.antrl4.SqlParser.Create_routine_param_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_routine_options}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_routine_options(org.apache.zeppelin.antrl4.SqlParser.Create_routine_optionsContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_routine_option}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_routine_option(org.apache.zeppelin.antrl4.SqlParser.Create_routine_optionContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#drop_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDrop_stmt(org.apache.zeppelin.antrl4.SqlParser.Drop_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#end_transaction_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEnd_transaction_stmt(org.apache.zeppelin.antrl4.SqlParser.End_transaction_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#exec_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExec_stmt(org.apache.zeppelin.antrl4.SqlParser.Exec_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#if_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIf_stmt(org.apache.zeppelin.antrl4.SqlParser.If_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#if_plsql_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIf_plsql_stmt(org.apache.zeppelin.antrl4.SqlParser.If_plsql_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#if_tsql_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIf_tsql_stmt(org.apache.zeppelin.antrl4.SqlParser.If_tsql_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#if_bteq_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIf_bteq_stmt(org.apache.zeppelin.antrl4.SqlParser.If_bteq_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#elseif_block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitElseif_block(org.apache.zeppelin.antrl4.SqlParser.Elseif_blockContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#else_block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitElse_block(org.apache.zeppelin.antrl4.SqlParser.Else_blockContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#include_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInclude_stmt(org.apache.zeppelin.antrl4.SqlParser.Include_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInsert_stmt(org.apache.zeppelin.antrl4.SqlParser.Insert_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_stmt_cols}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInsert_stmt_cols(org.apache.zeppelin.antrl4.SqlParser.Insert_stmt_colsContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_stmt_rows}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInsert_stmt_rows(org.apache.zeppelin.antrl4.SqlParser.Insert_stmt_rowsContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_stmt_row}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInsert_stmt_row(org.apache.zeppelin.antrl4.SqlParser.Insert_stmt_rowContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#insert_directory_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInsert_directory_stmt(org.apache.zeppelin.antrl4.SqlParser.Insert_directory_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#exit_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExit_stmt(org.apache.zeppelin.antrl4.SqlParser.Exit_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#get_diag_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGet_diag_stmt(org.apache.zeppelin.antrl4.SqlParser.Get_diag_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#get_diag_stmt_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGet_diag_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Get_diag_stmt_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#get_diag_stmt_exception_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGet_diag_stmt_exception_item(org.apache.zeppelin.antrl4.SqlParser.Get_diag_stmt_exception_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#get_diag_stmt_rowcount_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGet_diag_stmt_rowcount_item(org.apache.zeppelin.antrl4.SqlParser.Get_diag_stmt_rowcount_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#grant_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGrant_stmt(org.apache.zeppelin.antrl4.SqlParser.Grant_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#grant_stmt_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGrant_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Grant_stmt_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#leave_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLeave_stmt(org.apache.zeppelin.antrl4.SqlParser.Leave_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#map_object_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMap_object_stmt(org.apache.zeppelin.antrl4.SqlParser.Map_object_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#open_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOpen_stmt(org.apache.zeppelin.antrl4.SqlParser.Open_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#fetch_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFetch_stmt(org.apache.zeppelin.antrl4.SqlParser.Fetch_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#collect_stats_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCollect_stats_stmt(org.apache.zeppelin.antrl4.SqlParser.Collect_stats_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#collect_stats_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCollect_stats_clause(org.apache.zeppelin.antrl4.SqlParser.Collect_stats_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#close_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClose_stmt(org.apache.zeppelin.antrl4.SqlParser.Close_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cmp_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCmp_stmt(org.apache.zeppelin.antrl4.SqlParser.Cmp_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cmp_source}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCmp_source(org.apache.zeppelin.antrl4.SqlParser.Cmp_sourceContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_from_local_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCopy_from_local_stmt(org.apache.zeppelin.antrl4.SqlParser.Copy_from_local_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCopy_stmt(org.apache.zeppelin.antrl4.SqlParser.Copy_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_source}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCopy_source(org.apache.zeppelin.antrl4.SqlParser.Copy_sourceContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_target}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCopy_target(org.apache.zeppelin.antrl4.SqlParser.Copy_targetContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_option}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCopy_option(org.apache.zeppelin.antrl4.SqlParser.Copy_optionContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#copy_file_option}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCopy_file_option(org.apache.zeppelin.antrl4.SqlParser.Copy_file_optionContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#commit_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCommit_stmt(org.apache.zeppelin.antrl4.SqlParser.Commit_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_index_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_index_stmt(org.apache.zeppelin.antrl4.SqlParser.Create_index_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#create_index_col}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreate_index_col(org.apache.zeppelin.antrl4.SqlParser.Create_index_colContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#index_storage_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIndex_storage_clause(org.apache.zeppelin.antrl4.SqlParser.Index_storage_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#index_mssql_storage_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIndex_mssql_storage_clause(org.apache.zeppelin.antrl4.SqlParser.Index_mssql_storage_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#print_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrint_stmt(org.apache.zeppelin.antrl4.SqlParser.Print_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#quit_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitQuit_stmt(org.apache.zeppelin.antrl4.SqlParser.Quit_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#raise_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRaise_stmt(org.apache.zeppelin.antrl4.SqlParser.Raise_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#resignal_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitResignal_stmt(org.apache.zeppelin.antrl4.SqlParser.Resignal_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#return_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReturn_stmt(org.apache.zeppelin.antrl4.SqlParser.Return_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#rollback_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRollback_stmt(org.apache.zeppelin.antrl4.SqlParser.Rollback_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#set_session_option}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSet_session_option(org.apache.zeppelin.antrl4.SqlParser.Set_session_optionContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#set_current_schema_option}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSet_current_schema_option(org.apache.zeppelin.antrl4.SqlParser.Set_current_schema_optionContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#set_mssql_session_option}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSet_mssql_session_option(org.apache.zeppelin.antrl4.SqlParser.Set_mssql_session_optionContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#set_teradata_session_option}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSet_teradata_session_option(org.apache.zeppelin.antrl4.SqlParser.Set_teradata_session_optionContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#signal_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSignal_stmt(org.apache.zeppelin.antrl4.SqlParser.Signal_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#summary_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSummary_stmt(org.apache.zeppelin.antrl4.SqlParser.Summary_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#truncate_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTruncate_stmt(org.apache.zeppelin.antrl4.SqlParser.Truncate_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#use_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUse_stmt(org.apache.zeppelin.antrl4.SqlParser.Use_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#values_into_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValues_into_stmt(org.apache.zeppelin.antrl4.SqlParser.Values_into_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#while_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhile_stmt(org.apache.zeppelin.antrl4.SqlParser.While_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#for_cursor_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFor_cursor_stmt(org.apache.zeppelin.antrl4.SqlParser.For_cursor_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#for_range_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFor_range_stmt(org.apache.zeppelin.antrl4.SqlParser.For_range_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#label}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLabel(org.apache.zeppelin.antrl4.SqlParser.LabelContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#using_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUsing_clause(org.apache.zeppelin.antrl4.SqlParser.Using_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelect_stmt(org.apache.zeppelin.antrl4.SqlParser.Select_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cte_select_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCte_select_stmt(org.apache.zeppelin.antrl4.SqlParser.Cte_select_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cte_select_stmt_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCte_select_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Cte_select_stmt_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#cte_select_cols}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCte_select_cols(org.apache.zeppelin.antrl4.SqlParser.Cte_select_colsContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#fullselect_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFullselect_stmt(org.apache.zeppelin.antrl4.SqlParser.Fullselect_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#fullselect_stmt_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFullselect_stmt_item(org.apache.zeppelin.antrl4.SqlParser.Fullselect_stmt_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#fullselect_set_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFullselect_set_clause(org.apache.zeppelin.antrl4.SqlParser.Fullselect_set_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#subselect_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSubselect_stmt(org.apache.zeppelin.antrl4.SqlParser.Subselect_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelect_list(org.apache.zeppelin.antrl4.SqlParser.Select_listContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_set}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelect_list_set(org.apache.zeppelin.antrl4.SqlParser.Select_list_setContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_limit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelect_list_limit(org.apache.zeppelin.antrl4.SqlParser.Select_list_limitContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelect_list_item(org.apache.zeppelin.antrl4.SqlParser.Select_list_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_alias}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelect_list_alias(org.apache.zeppelin.antrl4.SqlParser.Select_list_aliasContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_list_asterisk}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelect_list_asterisk(org.apache.zeppelin.antrl4.SqlParser.Select_list_asteriskContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#into_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInto_clause(org.apache.zeppelin.antrl4.SqlParser.Into_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFrom_clause(org.apache.zeppelin.antrl4.SqlParser.From_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_table_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFrom_table_clause(org.apache.zeppelin.antrl4.SqlParser.From_table_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_table_name_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFrom_table_name_clause(org.apache.zeppelin.antrl4.SqlParser.From_table_name_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_subselect_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFrom_subselect_clause(org.apache.zeppelin.antrl4.SqlParser.From_subselect_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_join_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFrom_join_clause(org.apache.zeppelin.antrl4.SqlParser.From_join_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_join_type_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFrom_join_type_clause(org.apache.zeppelin.antrl4.SqlParser.From_join_type_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_table_values_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFrom_table_values_clause(org.apache.zeppelin.antrl4.SqlParser.From_table_values_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_table_values_row}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFrom_table_values_row(org.apache.zeppelin.antrl4.SqlParser.From_table_values_rowContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#from_alias_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFrom_alias_clause(org.apache.zeppelin.antrl4.SqlParser.From_alias_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#table_name}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTable_name(org.apache.zeppelin.antrl4.SqlParser.Table_nameContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#where_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhere_clause(org.apache.zeppelin.antrl4.SqlParser.Where_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#group_by_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGroup_by_clause(org.apache.zeppelin.antrl4.SqlParser.Group_by_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#having_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitHaving_clause(org.apache.zeppelin.antrl4.SqlParser.Having_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#qualify_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitQualify_clause(org.apache.zeppelin.antrl4.SqlParser.Qualify_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#order_by_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOrder_by_clause(org.apache.zeppelin.antrl4.SqlParser.Order_by_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_options}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelect_options(org.apache.zeppelin.antrl4.SqlParser.Select_optionsContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#select_options_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelect_options_item(org.apache.zeppelin.antrl4.SqlParser.Select_options_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#update_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUpdate_stmt(org.apache.zeppelin.antrl4.SqlParser.Update_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#update_assignment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUpdate_assignment(org.apache.zeppelin.antrl4.SqlParser.Update_assignmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#update_table}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUpdate_table(org.apache.zeppelin.antrl4.SqlParser.Update_tableContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#update_upsert}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUpdate_upsert(org.apache.zeppelin.antrl4.SqlParser.Update_upsertContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#merge_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMerge_stmt(org.apache.zeppelin.antrl4.SqlParser.Merge_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#merge_table}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMerge_table(org.apache.zeppelin.antrl4.SqlParser.Merge_tableContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#merge_condition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMerge_condition(org.apache.zeppelin.antrl4.SqlParser.Merge_conditionContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#merge_action}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMerge_action(org.apache.zeppelin.antrl4.SqlParser.Merge_actionContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#delete_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDelete_stmt(org.apache.zeppelin.antrl4.SqlParser.Delete_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#delete_alias}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDelete_alias(org.apache.zeppelin.antrl4.SqlParser.Delete_aliasContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#describe_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDescribe_stmt(org.apache.zeppelin.antrl4.SqlParser.Describe_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBool_expr(org.apache.zeppelin.antrl4.SqlParser.Bool_exprContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBool_expr_atom(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_atomContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_unary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBool_expr_unary(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_unaryContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_single_in}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBool_expr_single_in(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_single_inContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_multi_in}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBool_expr_multi_in(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_multi_inContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_binary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBool_expr_binary(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_binaryContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_logical_operator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBool_expr_logical_operator(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_logical_operatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_expr_binary_operator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBool_expr_binary_operator(org.apache.zeppelin.antrl4.SqlParser.Bool_expr_binary_operatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr(org.apache.zeppelin.antrl4.SqlParser.ExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_atom(org.apache.zeppelin.antrl4.SqlParser.Expr_atomContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_interval}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_interval(org.apache.zeppelin.antrl4.SqlParser.Expr_intervalContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#interval_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInterval_item(org.apache.zeppelin.antrl4.SqlParser.Interval_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_concat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_concat(org.apache.zeppelin.antrl4.SqlParser.Expr_concatContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_concat_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_concat_item(org.apache.zeppelin.antrl4.SqlParser.Expr_concat_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_case}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_case(org.apache.zeppelin.antrl4.SqlParser.Expr_caseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_case_simple}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_case_simple(org.apache.zeppelin.antrl4.SqlParser.Expr_case_simpleContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_case_searched}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_case_searched(org.apache.zeppelin.antrl4.SqlParser.Expr_case_searchedContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_cursor_attribute}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_cursor_attribute(org.apache.zeppelin.antrl4.SqlParser.Expr_cursor_attributeContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_agg_window_func}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_agg_window_func(org.apache.zeppelin.antrl4.SqlParser.Expr_agg_window_funcContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func_all_distinct}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_func_all_distinct(org.apache.zeppelin.antrl4.SqlParser.Expr_func_all_distinctContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func_over_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_func_over_clause(org.apache.zeppelin.antrl4.SqlParser.Expr_func_over_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func_partition_by_clause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_func_partition_by_clause(org.apache.zeppelin.antrl4.SqlParser.Expr_func_partition_by_clauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_spec_func}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_spec_func(org.apache.zeppelin.antrl4.SqlParser.Expr_spec_funcContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_func(org.apache.zeppelin.antrl4.SqlParser.Expr_funcContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_func_params}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_func_params(org.apache.zeppelin.antrl4.SqlParser.Expr_func_paramsContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#func_param}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunc_param(org.apache.zeppelin.antrl4.SqlParser.Func_paramContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_select}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_select(org.apache.zeppelin.antrl4.SqlParser.Expr_selectContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#expr_file}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr_file(org.apache.zeppelin.antrl4.SqlParser.Expr_fileContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#hive}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitHive(org.apache.zeppelin.antrl4.SqlParser.HiveContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#hive_item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitHive_item(org.apache.zeppelin.antrl4.SqlParser.Hive_itemContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#host}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitHost(org.apache.zeppelin.antrl4.SqlParser.HostContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#host_cmd}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitHost_cmd(org.apache.zeppelin.antrl4.SqlParser.Host_cmdContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#host_stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitHost_stmt(org.apache.zeppelin.antrl4.SqlParser.Host_stmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#file_name}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFile_name(org.apache.zeppelin.antrl4.SqlParser.File_nameContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#date_literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDate_literal(org.apache.zeppelin.antrl4.SqlParser.Date_literalContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#timestamp_literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTimestamp_literal(org.apache.zeppelin.antrl4.SqlParser.Timestamp_literalContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#ident}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdent(org.apache.zeppelin.antrl4.SqlParser.IdentContext ctx);
	/**
	 * Visit a parse tree produced by the {@code single_quotedString}
	 * labeled alternative in {@link org.apache.zeppelin.antrl4.SqlParser#string}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSingle_quotedString(org.apache.zeppelin.antrl4.SqlParser.Single_quotedStringContext ctx);
	/**
	 * Visit a parse tree produced by the {@code double_quotedString}
	 * labeled alternative in {@link org.apache.zeppelin.antrl4.SqlParser#string}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDouble_quotedString(org.apache.zeppelin.antrl4.SqlParser.Double_quotedStringContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#int_number}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInt_number(org.apache.zeppelin.antrl4.SqlParser.Int_numberContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#dec_number}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDec_number(org.apache.zeppelin.antrl4.SqlParser.Dec_numberContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#bool_literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBool_literal(org.apache.zeppelin.antrl4.SqlParser.Bool_literalContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#null_const}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNull_const(org.apache.zeppelin.antrl4.SqlParser.Null_constContext ctx);
	/**
	 * Visit a parse tree produced by {@link org.apache.zeppelin.antrl4.SqlParser#non_reserved_words}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNon_reserved_words(org.apache.zeppelin.antrl4.SqlParser.Non_reserved_wordsContext ctx);
}