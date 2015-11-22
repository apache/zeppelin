echo 'From your host machine,' 
echo 'git clone the incubator-zeppelin branch into this directory'
echo
echo 'vagrant ssh'
echo
echo '# then when running inside the VM'
echo
echo 'cd /vagrant/incubator-zeppelin'
echo 'mvn clean package -DskipTests'
echo
echo '# or for a specific build'
echo
echo 'mvn clean package -Pspark-1.5 -Ppyspark -Dhadoop.version=2.2.0 -Phadoop-2.2 -DskipTests'
