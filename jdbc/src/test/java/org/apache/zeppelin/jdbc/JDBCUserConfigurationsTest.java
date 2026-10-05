/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.jdbc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JDBCUserConfigurationsTest {

  @Test
  void cancelStatementBeforeSaveShouldNotThrowNPE() {
    JDBCUserConfigurations jdbcUserConfigurations = new JDBCUserConfigurations();

    assertDoesNotThrow(() -> jdbcUserConfigurations.cancelStatement("paragraph-not-registered"));
  }

  @Test
  void cancelStatementAfterSaveShouldCallCancelOnStatement() throws SQLException {
    JDBCUserConfigurations jdbcUserConfigurations = new JDBCUserConfigurations();
    Statement statement = Mockito.mock(Statement.class);
    jdbcUserConfigurations.saveStatement("paragraph-1", statement);

    jdbcUserConfigurations.cancelStatement("paragraph-1");

    verify(statement).cancel();
  }

  @Test
  void cancelStatementAfterRemoveShouldNotThrowNPE() throws SQLException {
    JDBCUserConfigurations jdbcUserConfigurations = new JDBCUserConfigurations();
    Statement statement = Mockito.mock(Statement.class);
    jdbcUserConfigurations.saveStatement("paragraph-1", statement);
    jdbcUserConfigurations.removeStatement("paragraph-1");

    assertDoesNotThrow(() -> jdbcUserConfigurations.cancelStatement("paragraph-1"));
    verify(statement, never()).cancel();
  }

  @Test
  void nullParagraphIdShouldBeNoOpAcrossAllMapOperations() throws SQLException {
    JDBCUserConfigurations jdbcUserConfigurations = new JDBCUserConfigurations();
    Statement statement = Mockito.mock(Statement.class);

    assertDoesNotThrow(() -> jdbcUserConfigurations.saveStatement(null, statement));
    assertDoesNotThrow(() -> jdbcUserConfigurations.cancelStatement(null));
    assertDoesNotThrow(() -> jdbcUserConfigurations.removeStatement(null));
    verify(statement, never()).cancel();
  }
}
