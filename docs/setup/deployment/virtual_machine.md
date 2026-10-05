---
layout: page
title: "Apache Zeppelin on Vagrant Virtual Machine"
description: "The Vagrant developer environment under scripts/vagrant/zeppelin-dev is deprecated and unmaintained. Build Apache Zeppelin from source instead."
group: setup/deployment 
---
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
{% include JB/setup %}

# Apache Zeppelin on Vagrant Virtual Machine (deprecated)

<div id="toc"></div>

## Deprecation notice

**Deprecated as of 0.13.0.** The Vagrant environment in
`scripts/vagrant/zeppelin-dev` is no longer maintained and is not expected to
provision a usable Zeppelin build environment. See
[ZEPPELIN-6460](https://issues.apache.org/jira/browse/ZEPPELIN-6460).

Apache Zeppelin ships a script directory `scripts/vagrant/zeppelin-dev`, holding
a Vagrant machine definition and the Ansible roles that provision it with the
dependencies once required for developing Zeppelin.

To build Zeppelin, follow
[How to Build Zeppelin from source](../basics/how_to_build.html) instead. A local
checkout needs only Git and a JDK; `./mvnw` supplies Maven, and each web module
downloads its own Node.js and npm.

## What the VM provisions

On top of the Ubuntu 16.04 box, the Ansible roles install:

 - openjdk-8-jdk
 - Maven 3.3.9
 - Node.js and npm from the distribution packages
 - ruby + rake, make and bundler (only required if building the jekyll documentation)
 - git, curl and unzip
 - libfontconfig, to avoid PhantomJS missing dependency issues
 - Python addons: pip, matplotlib, scipy, numpy, pandas

## Why it is deprecated

The toolchain provisioned here has not been updated since 2018, and the
provisioning now fails for several independent reasons:

* The `ubuntu/xenial64` box is Ubuntu 16.04, which is past end of life.
* `ansible-roles.yml` installs `python-minimal` and `python-simplejson`, and the
  `python-addons` role installs `python-pip`, `python-matplotlib`,
  `python-scipy`, `python-numpy` and `python-pandas`. These are Python 2
  packages that no longer exist on supported Ubuntu releases, and current
  `ansible-core` requires Python 3 on the managed node.
* The `maven` role resolves Maven 3.3.9 through `closer.cgi`. That request still
  succeeds, but the mirror it returns answers 404: Maven 3.3.9 has been moved off
  the distribution mirrors and is only on `archive.apache.org`.
* The `common` role installs `libfontconfig` for PhantomJS, which the project no
  longer uses.
* The toolchain it provisions is far below the project baseline: OpenJDK 8
  instead of 11, Maven 3.3.9 instead of 3.6.3, and the distribution `nodejs`
  package on Xenial, which is Node.js 4.2.6.

## Current project baseline

These are the versions the build actually expects. They are defined in the build
itself, not in the Vagrant scripts.

<table class="table-configuration">
  <tr>
    <th>Component</th>
    <th>Version</th>
    <th>Source of truth</th>
  </tr>
  <tr>
    <td>OpenJDK or Oracle JDK</td>
    <td>11</td>
    <td><code>java.version</code> in the root <code>pom.xml</code></td>
  </tr>
  <tr>
    <td>Maven</td>
    <td>3.6.3 or higher</td>
    <td><code>maven.version</code> in the root <code>pom.xml</code></td>
  </tr>
  <tr>
    <td>Node.js / npm</td>
    <td>22.21.1 / 10.9.4</td>
    <td><code>node.version</code> / <code>npm.version</code> in <code>zeppelin-web-angular/pom.xml</code></td>
  </tr>
</table>

Only the JDK has to be installed by hand. `./mvnw` downloads Maven, and
`frontend-maven-plugin` downloads the pinned Node.js and npm into each web
module, so neither has to be on `PATH`. Building the classic UI with
`-Pweb-classic` pulls in `zeppelin-web`, which pins its own pair in
`zeppelin-web/pom.xml`.

## Create a Zeppelin Ready VM

This setup requires [Vagrant](https://developer.hashicorp.com/vagrant/install),
[VirtualBox](https://www.virtualbox.org/) and
[Ansible](https://docs.ansible.com/ansible/latest/installation_guide/intro_installation.html).
With all three installed, run `vagrant up` from within the
`scripts/vagrant/zeppelin-dev` directory, then `vagrant ssh` to get a shell on
the guest machine.

If you don't wish to build Zeppelin from scratch, run the z-manager installer
script while running in the guest VM:

```bash
curl -fsSL https://raw.githubusercontent.com/NFLabs/z-manager/master/zeppelin-installer.sh | bash
```

## Building Zeppelin in the VM

Clone the project over HTTPS, on your host machine or directly in the VM:

```bash
git clone https://github.com/apache/zeppelin.git
```

Cloning the project again may seem counter intuitive, since this script
originated from the project repository. Consider copying just the
`vagrant/zeppelin-dev` script from the Zeppelin project as a stand alone
directory, then once again clone the specific branch you wish to build.

Cloning Zeppelin into the `scripts/vagrant/zeppelin-dev` directory from the host
will allow the directory to be shared between your host and the guest machine.
By default, Vagrant shares the directory holding the `Vagrantfile` to `/vagrant`
in the guest, so the checkout is reachable at `/vagrant/zeppelin`.
_[(1) Synced Folder Description from Vagrant Up](https://docs.vagrantup.com/v2/synced-folders/index.html)_

```bash
cd /vagrant/zeppelin
./mvnw clean package -DskipTests
./bin/zeppelin-daemon.sh start
```

On your host machine browse to `http://localhost:8080/`.

If you [turned off port forwarding](#tweaking-the-virtual-machine) in the
`Vagrantfile`, browse to `http://192.168.51.52:8080` instead.

See [Build profiles](../basics/how_to_build.html#build-profiles) for the Spark,
Flink and Hadoop profiles this project currently supports.

## Tweaking the Virtual Machine

If you plan to run this virtual machine alongside other Vagrant images, you may
wish to bind the virtual machine to a specific IP address instead of forwarding
ports from your local host.

Comment out the `forwarded_port` lines, and uncomment the `private_network` line
in the `Vagrantfile`. The subnet that works best for your local network will
vary, so adjust `192.168.*.*` accordingly.

```
#config.vm.network "forwarded_port", guest: 8080, host: 8080
config.vm.network "private_network", ip: "192.168.51.52"
```

`vagrant halt` followed by `vagrant up` will restart the guest machine bound to
`192.168.51.52`. This is typically required when running other virtual machines
that discover each other directly by IP address.
