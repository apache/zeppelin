# Interpreter Overview #

The Zeppelin JDBC interpreter is meant to connect to JDBC backends whose drivers cannot be packaged with 
Zeppelin due to licensing concerns. Examples are: SQL Server, Mysql. This interpreter is currently
only compatible with those and Postgresql, although it is easy to add support for your favorite JDBC backend.

### Setting up a driver

You should download your JDBC driver and place the .jar file somewhere that the interpreter can locate it, 
e.g., "your_zeppelin_home"/interpreter/jdbc/. The interpreter will load and register your driver dynamically.

### Interpreter Settings

After launching Zeppelin, go to the interpreter settings menu, create a '%jdbc' interpreter, and set your 
driver name, type, and location.

### Adding support for a JDBC backend

Since this interpreter uses java.sql.DriverManager, it supports all JDBC drivers with the same java code. 
However, JDBC driver names and connection url formats vary. The only thing needed to support a new backend 
is to modify JDBCConnectionUrlBuilder.java, and add a connection url method to use the right format for 
your backend.



