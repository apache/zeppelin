# How to contribute

**Zeppelin** is [Apache2 License](https://github.com/apache/incubator-zeppelin/blob/master/CONTRIBUTING.md) Software.
Contributing to Zeppelin (Source code, Documents, Image, Website) means you agree to the Apache2 License.

1. Make sure your issue is not already in the [Jira issue tracker](https://issues.apache.org/jira/browse/ZEPPELIN)
2. If not, create a ticket describing the change you're proposing in the [Jira issue tracker](https://issues.apache.org/jira/browse/ZEPPELIN)
3. Contribute your patch via Pull Request.

Before you start, please read the [Code of Conduct](http://www.apache.org/foundation/policies/conduct.html) carefully, familiarize yourself with it and refer to it whenever you need it.

For those of you who are not familiar with Apache project, understanding [How it works](http://www.apache.org/foundation/how-it-works.html) would be quite helpful.

## Creating a Pull Request
In order to make the review process easier, please follow this template when making a Pull Request:

```
### What is this PR for?
A few sentences describing the overall goals of the pull request's commits.
First time? Check out the contributing guide - https://github.com/apache/incubator-zeppelin/blob/master/CONTRIBUTING.md

### What type of PR is it?
[Bug Fix | Improvement | Feature | Documentation | Hot Fix | Refactoring]

### Todos
* [ ] - Task

### What is the Jira issue?
* Open an issue on Jira https://issues.apache.org/jira/browse/ZEPPELIN/
* Put link here, and add [ZEPPELIN-*Jira number*] in PR title, eg. [ZEPPELIN-533]

### How should this be tested?
Outline the steps to test the PR here.

### Screenshots (if appropriate)

### Questions:
* Does the licenses files need update?
* Is there breaking changes for older versions?
* Does this needs documentation?
```

## Testing a Pull Request
You can also test and review a particular Pull Request. Here are two useful ways.

* Using a utility provided from Zeppelin. 
    
    ```
    dev/test_zeppelin_pr.py [# of PR]
    ```

    For example, if you want to test `#513`, then the command will be:

    ```
    dev/test_zeppelin_pr.py 513
    ```

* Another way is using [github/hub](https://github.com/github/hub). 
     
    ```
    hub checkout https://github.com/apache/incubator-zeppelin/pull/[# of PR]
    ```

The above two methods will help you test and review Pull Requests.

## Source Control Workflow
Zeppelin follows [Fork & Pull] (https://github.com/sevntu-checkstyle/sevntu.checkstyle/wiki/Development-workflow-with-Git:-Fork,-Branching,-Commits,-and-Pull-Request) model.

## The Review Process

When a Pull Request is submitted, it is being merged or rejected by following review process.

* Anybody can be a reviewer and may comment on the change and suggest modifications.
* Reviewer can indicate that a patch looks suitable for merging with a comment such as: "Looks good", "LGTM", "+1".
* At least one indication of suitable for merging (e.g. "LGTM") from committer is required to be merged.
* Pull request is open for 1 or 2 days for potential additional review, unless it's got enough indication of suitable for merging.
* Committer can initiate lazy consensus ("Merge if there is no more discussion") and the code can be merged after certain time (normally 24 hours) when there is no review exists.
* Contributor can ping reviewers (including committer) by commenting 'Ready to review' or suitable indication.

## Becoming a Committer

The PPMC adds new committers from the active contributors, based on their contribution to Zeppelin. The qualifications for new committers include:

1. Sustained contributions: Committers should have a history of constant contributions to Zeppelin.
2. Quality of contributions: Committers more than any other community member should submit simple, well-tested, and well-designed patches.
3. Community involvement: Committers should have a constructive and friendly attitude in all community interactions. They should also be active on the dev, user list and reviewing patches. Also help new contributors and users.


## Setting up
Here are some things you will need to build and test Zeppelin.

### Software Configuration Management (SCM)

Zeppelin uses Git for its SCM system. `http://git.apache.org/incubator-zeppelin.git` you'll need git client installed in your development machine.
For write access, `https://git-wip-us.apache.org/repos/asf/incubator-zeppelin.git`

### Integrated Development Environment (IDE)

You are free to use whatever IDE you prefer, or your favorite command line editor.
 
### Project Structure

Zeppelin project is based on Maven. Maven works by convention & defines [directory structure] (https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html) for a project.
The top-level pom.xml describes the basic project structure. Currently Zeppelin has the following modules.

    <module>zeppelin-interpreter</module>
    <module>zeppelin-zengine</module>
    <module>spark</module>
    <module>markdown</module>
    <module>angular</module>
    <module>shell</module>
    <module>hive</module>
    <module>tajo</module>
    <module>flink</module>
    <module>ignite</module>
    <module>lens</module>
    <module>cassandra</module>
    <module>zeppelin-web</module>
    <module>zeppelin-server</module>
    <module>zeppelin-distribution</module>
    
### Web Project Contribution Guidelines
If you plan on making a contribution to Zeppelin's WebApplication,
please check [its own contribution guidelines](https://github.com/apache/incubator-zeppelin/blob/master/zeppelin-web/CONTRIBUTING.md)

### Code convention
We are following Google Code style:
* [Java style](http://google-styleguide.googlecode.com/svn/trunk/javaguide.html) 
* [Shell style](https://google-styleguide.googlecode.com/svn/trunk/shell.xml)

Check style report location are in `${submodule}/target/site/checkstyle.html`
Test coverage report location are in `${submodule}/target/site/cobertura/index.html`

#### Build Tools

To build the code, install
 * Oracle Java 7
 * Apache Maven

## Getting the source code
First of all, you need the Zeppelin source code. The official location for Zeppelin is [http://git.apache.org/incubator-zeppelin.git](http://git.apache.org/incubator-zeppelin.git).

### git access

Get the source code on your development machine using git.

```
git clone git://git.apache.org/incubator-zeppelin.git zeppelin
```

You may also want to develop against a specific branch. For example, for branch-0.5.6

```
git clone -b branch-0.5.6 git://git.apache.org/incubator-zeppelin.git zeppelin
```

or with write access

```
git clone https://git-wip-us.apache.org/repos/asf/incubator-zeppelin.git
```

### Fork repository

If you want not only build Zeppelin but also make change, then you need fork Zeppelin github mirror repository (https://github.com/apache/incubator-zeppelin) and make pull request.


## Build

```
mvn install
```

To skip test

```
mvn install -DskipTests
```

To build with specific spark / hadoop version

```
mvn install -Phadoop-2.2 -Dhadoop.version=2.2.0 -Pspark-1.3 -Dspark.version=1.3.0
```

## Tests
Each new File should have its own accompanying unit tests. Each new interpreter should have come with its tests.

  
Zeppelin has 3 types of tests:

  1. Unit Tests: The unit tests run as part of each package's build. E.g. SparkInterpeter Module's unit test is SparkInterpreterTest
  2. Integration Tests: The integration tests run after all modules are build. The integration tests launch an instance of Zeppelin server. ZeppelinRestApiTest is an example integration test. 
  3. GUI integration tests: These tests validate the Zeppelin UI elements. These tests require a running Zeppelin server and launches a web browser to validate Notebook UI elements like Notes and their execution. See ZeppelinIT as an example.  

Currently the GUI integration tests are not run in the Maven and are only run in the CI environment when the pull request is submitted to github. Make sure to watch the [CI results] (https://travis-ci.org/apache/incubator-zeppelin/pull_requests) for your pull request.

## Continuous Integration

Zeppelin uses Travis for CI. In the project root there is .travis.yml that configures CI and [publishes CI results] (https://travis-ci.org/apache/incubator-zeppelin/builds)
  

## Run Zeppelin server in development mode

```
cd zeppelin-server
HADOOP_HOME=YOUR_HADOOP_HOME JAVA_HOME=YOUR_JAVA_HOME mvn exec:java -Dexec.mainClass="com.nflabs.zeppelin.server.ZeppelinServer" -Dexec.args=""
```

or use daemon script

```
bin/zeppelin-daemon start
```


Server will be run on http://localhost:8080

## JIRA
Zeppelin manages it's issues in Jira. [https://issues.apache.org/jira/browse/ZEPPELIN](https://issues.apache.org/jira/browse/ZEPPELIN)

## Stay involved
Everyone is welcome to join our mailing list:

 * [users@zeppelin.incubator.apache.org](http://mail-archives.apache.org/mod_mbox/incubator-zeppelin-users/) is for usage questions, help, and announcements [ [subscribe](mailto:users-subscribe@zeppelin.incubator.apache.org?subject=send%20this%20email%20to%20subscribe), [unsubscribe](mailto:users-unsubscribe@zeppelin.incubator.apache.org?subject=send%20this%20email%20to%20unsubscribe), [archive](http://mail-archives.apache.org/mod_mbox/incubator-zeppelin-users/) ]
 * [dev@zeppelin.incubator.apache.org](http://mail-archives.apache.org/mod_mbox/incubator-zeppelin-users/) is for people who want to contribute code to Zeppelin.[ [subscribe](mailto:dev-subscribe@zeppelin.incubator.apache.org?subject=send%20this%20email%20to%20subscribe), [unsubscribe](mailto:dev-unsubscribe@zeppelin.incubator.apache.org?subject=send%20this%20email%20to%20unsubscribe), [archive](http://mail-archives.apache.org/mod_mbox/incubator-zeppelin-dev/) ]
 * [commits@zeppelin.incubator.apache.org](http://mail-archives.apache.org/mod_mbox/incubator-zeppelin-commits/) is for commit messages and patches to Zeppelin. [ [subscribe](mailto:commits-subscribe@zeppelin.incubator.apache.org?subject=send%20this%20email%20to%20subscribe), [unsubscribe](mailto:commits-unsubscribe@zeppelin.incubator.apache.org?subject=send%20this%20email%20to%20unsubscribe), [archive](http://mail-archives.apache.org/mod_mbox/incubator-zeppelin-commits/) ]
