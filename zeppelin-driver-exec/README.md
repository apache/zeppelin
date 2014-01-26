## zeppelin-driver-exec

Shell execution driver for [Zeppelin](http://zeppelin-project.org).

Allow Zeppelin runs shell command.

### Configuration

Add driver uri in *zeppelin.drivers* property. assume driver files are under /drivers/exec and want to name it 'exec'.

ex)

    <property>
       <name>zeppelin.drivers</name>
       <value>exec:exec:com.nflabs.zeppelin.driver.exec.ExecDriver:exec://</value>
    </property>




###Usage


    @driver set exec;
    echo 'hello world';
    date;

