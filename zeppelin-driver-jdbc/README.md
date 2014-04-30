## zeppelin-driver-jdbc

JDBC driver for [Zeppelin](http://zeppelin-project.org).

Allow Zeppelin runs any JDBC driver implementations

### Configuration

driver configuration uri should look like

    [name]:jdbc:[classname]:[connection url]


Add this driver uri in *zeppelin.drivers* property. for example,

ex)

    <property>
       <name>zeppelin.drivers</name>
       <value>mysql:jdbc:com.mysql.jdbc.Driver:mysql://localhost/mydb?user=user1&password=pass1
</value>
    </property>




###Usage

    @driver set mysql;
    use mysql;
    select * from user;

