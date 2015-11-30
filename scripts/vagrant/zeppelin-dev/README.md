<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
## Vagrant Virtual Machine for Apache Zeppelin
  
This script creates a virtual machine that launches a repeatable, known set of core dependencies required for developing Zeppelin.  It can also be used to run an existing Zeppelin build if you don't plan to build from source.  For pyspark users, this script also includes several helpful [Python Libraries and one obscure configuration to help with matplotlib plotting inside Zeppelin](#pythonextras)
 
####Installing the required components to launch a virtual machine.

This script requires three applications, [Ansible](http://docs.ansible.com/ansible/intro_installation.html#latest-releases-via-pip "Ansible"), [Vagrant](http://www.vagrantup.com/downloads "Vagrant") and [Virtual Box](https://www.virtualbox.org/ "Virtual Box").  All of these applications are freely available as Open Source projects and extremely easy to set up on most operating systems.

### Create a Zeppelin Ready VM in 4 Steps (5 on Windows)

*If you are running Windows and don't yet have python installed, install Python 2.7.x*  [Python Windows Installer](https://www.python.org/downloads/release/python-2710/)

1. Download and Install Vagrant:  [Vagrant Downloads](http://www.vagrantup.com/downloads)
2. Install Ansible:  [Ansible Python pip install](http://docs.ansible.com/ansible/intro_installation.html#latest-releases-via-pip)  
`sudo easy_install pip` then   
`sudo pip install ansible`  
`ansible --version` should now report version 1.9.2 or higher
3. Install Virtual Box: [Virtual Box Downloads](https://www.virtualbox.org/ "Virtual Box")
4. Type `vagrant up`  from within the `/scripts/vagrant/zeppelin-dev` directory

Thats it!

You can now run `vagrant ssh` and this will place you into the guest machines terminal prompt.

If you don't wish to build Zeppelin from scratch, run the z-manager installer script while running in the guest VM:

```
curl -fsSL https://raw.githubusercontent.com/NFLabs/z-manager/master/zeppelin-installer.sh | bash
```



### Building Zeppelin

You can now `git clone https://github.com/apache/incubator-zeppelin.git` into a directory on your host machine, or directly in your virtual machine.

Cloning zeppelin into the `/scripts/vagrant/zeppelin-dev` directory from the host, will allow the directory to be shared between your host and the guest machine.

Cloning the project again may seem counter intuitive, since this script likley originated from the project repository.  Consider copying just the vagrant/zeppelin-dev script from the zeppelin project as a stand alone directory, then once again clone the specific branch you wish to build.

Synced folders enable Vagrant to sync a folder on the host machine to the guest machine, allowing you to continue working on your project's files on your host machine, but use the resources in the guest machine to compile or run your project. _[(1) Synced Folder Description from Vagrant Up](https://docs.vagrantup.com/v2/synced-folders/index.html)_

By default, Vagrant will share your project directory (the directory with the Vagrantfile) to `/vagrant`.  Which means you should be able to build within the guest machine after you   
`cd /vagrant/incubator-zeppelin`


### What's in this VM?

Running the following commands in the guest machine should display these expected versions:

`node --version` should report *v0.12.7*  
`mvn --version` should report *Apache Maven 3.3.3* and *Java version: 1.7.0_85*


The virtual machine consists of:

 - Ubuntu Server 14.04 LTS
 - Node.js 0.12.7
 - npm 2.11.3
 - ruby 1.9.3 + rake, make and bundler (only required if building jekyll documentation)
 - Maven 3.3.3
 - Git
 - Unzip
 - libfontconfig to avoid phatomJs missing dependency issues
 - openjdk-7-jdk
 - Python addons: pip, matplotlib, scipy, numpy, pandas
 
### How to build & run Zeppelin

This assumes you've already cloned the project either on the host machine in the zeppelin-dev directory (to be shared with the guest machine) or cloned directly into a directory while running inside the guest machine.

```
cd /incubator-zeppelin
mvn clean package -Pspark-1.5 -Ppyspark -Dhadoop.version=2.2.0 -Phadoop-2.2 -DskipTests
./bin/zeppelin-daemon.sh start
```

On your host machine browse to `http://localhost:8080/`

If you [turned off port forwarding](#tweakvm) in the `Vagrantfile` browse to `http://192.168.51.52:8080`


### [Tweaking the Virtual Machine](id:tweakvm)

If you plan to run this virtual machine along side other Vagrant images, you may wish to bind the virtual machine to a specific IP address, and not use port fowarding from your local host.

Comment out the `forward_port` line, and uncomment the `private_network` line in Vagrantfile.  The subnet that works best for your local network will vary so adjust `192.168.*.*` accordingly.

```
#config.vm.network "forwarded_port", guest: 8080, host: 8080
config.vm.network "private_network", ip: "192.168.51.52"
```

`vagrant halt` followed by `vagrant up` will restart the guest machine bound to the IP address of `192.168.51.52`.  
This approach usually is typically required if running other virtual machines that discover each other directly by IP address, such as Spark Masters and Slaves as well as Cassandra Nodes, Elasticsearch Nodes, and other Spark data sources.  You may wish to launch nodes in virtual machines with IP Addresses in a subnet that works for your local network, such as: 192.168.51.53, 192.168.51.54, 192.168.51.53, etc..


### [Python Extras](id:pythonextras)

With zeppelin running, Numpy, SciPy, Pandas and Matplotlib will be available.  Create a pyspark notebook, and try

```
%pyspark

import numpy
import scipy
import pandas
import matplotlib

print "numpy " + numpy.__version__ 
print "scipy " + scipy.__version__
print "pandas " + pandas.__version__
print "matplotlib " + matplotlib.__version__
```

To Test plotting using matplotlib into a rendered %html SVG image, try

```
%pyspark

import matplotlib
matplotlib.use('Agg')   # turn off interactive charting so this works for server side SVG rendering
import matplotlib.pyplot as plt
import numpy as np
import StringIO

# clear out any previous plots on this notebook
plt.clf()

def show(p):
    img = StringIO.StringIO()
    p.savefig(img, format='svg')
    img.seek(0)
    print "%html <div style='width:600px'>" + img.buf + "</div>"

# Example data
people = ('Tom', 'Dick', 'Harry', 'Slim', 'Jim')
y_pos = np.arange(len(people))
performance = 3 + 10 * np.random.rand(len(people))
error = np.random.rand(len(people))

plt.barh(y_pos, performance, xerr=error, align='center', alpha=0.4)
plt.yticks(y_pos, people)
plt.xlabel('Performance')
plt.title('How fast do you want to go today?')

show(plt)
``` 






