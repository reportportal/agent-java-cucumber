# Cucumber Agent for ReportPortal
 [ ![Download](https://api.bintray.com/packages/epam/reportportal/agent-java-cucumber/images/download.svg) ](https://bintray.com/epam/reportportal/agent-java-cucumber/_latestVersion)
 
[![Join Slack chat!](https://reportportal-slack-auto.herokuapp.com/badge.svg)](https://reportportal-slack-auto.herokuapp.com)
[![stackoverflow](https://img.shields.io/badge/reportportal-stackoverflow-orange.svg?style=flat)](http://stackoverflow.com/questions/tagged/reportportal)
[![UserVoice](https://img.shields.io/badge/uservoice-vote%20ideas-orange.svg?style=flat)](https://rpp.uservoice.com/forums/247117-report-portal)
[![Build with Love](https://img.shields.io/badge/build%20with-❤%EF%B8%8F%E2%80%8D-lightgrey.svg)](http://reportportal.io?style=flat)


### Installation

Add to POM.xml

**dependency**

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
<repositories>
     <repository>
        <snapshots>
          <enabled>false</enabled>
        </snapshots>
        <id>bintray-epam-reportportal</id>
        <name>bintray</name>
        <url>http://dl.bintray.com/epam/reportportal</url>
     </repository>
</repositories>



<dependency>
  <groupId>com.epam.reportportal</groupId>
  <artifactId>agent-java-cucumber</artifactId>
  <version>3.0.1</version>
</dependency>
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

### Install Reporter

As Cucumber runs your features, it calls out to any number of listener objects to let them know 
how it’s progressing. These listeners are notified at various points throughout the run of features. 
This principle is used to notify ReportPortal about your tests progress in real-time. 
ReportPortal supports two kinds of Reporters. 
Both of them allow you to report your execution progress to ReportPortal, 
but there are some differences in report structure.  

* **Scenario Reporter** 
* **Step Reporter**
Step Reporter propagates the most traditional for ReportPortal test structure 
keeping your scenarios and steps inside as separate entities. In opposite, Scenario Reporter 
use scenario as the base point and does not separate step from each other which is sometimes more
convenient for BDD users.  

Enabling **StepReporter**:
```java
@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "com.epam.reportportal.cucumber.StepReporter"})
public class RunCukesTest {
}
```

Enabling **ScenarioReporter**:
```java
@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "com.epam.reportportal.cucumber.ScenarioReporter"})
public class RunCukesTest {
}
```


### Configuration

Copy you configuration from UI of Report Portal at [User Profile](<#user-profile>) section

or

In order to start using of agent, user should configure property file
"reportportal.properties" in such format:

**reportportal.properties**

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
rp.endpoint = https://rp.epam.com/
rp.username = default
rp.uuid = 8967de3b-fec7-47bb-9dbc-2aa4ceab8b1e
rp.launch = default_TEST_EXAMPLE
rp.project = default_project

## OPTIONAL PARAMETERS
rp.tags = TAG1;TAG2
rp.keystore.resource = reportportal-client-v2.jks
rp.keystore.password = reportportal

rp.batch.size.logs = 5
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


**Parameters**

User should provide next parameters to agent.

| **Parameter**                                 | **Description**      | **Required**|
|-----------------------------------------------|----------------------|-------------|
|rp.enable                                      |Enable/Disable logging to report portal: rp.enable=true - enable log to RP server.  Any other value means 'false': rp.enable=false - disable log to RP server.  If parameter is skipped in properties file then automation project results will be posted on RP. |No |
|rp.username                                    |User name |Yes |
|rp.password                                    |User password. **We strongly recommend to use UUID** or separate ReportPortal internal users password here to avoid domain password publishing. |Yes |
|rp.uuid                                        |UUID of user. |Yes |
|rp.endpoint                                    |URL of web service, where requests should be sent |Yes |
|rp.launch                                      |The unique name of Launch (Run), based on that name a history of runs will be created for the particular name |Yes |
|rp.project                                     |Project name, to identify scope |Yes |
|rp.tags                                        |Set of tags for specifying additional meta information for current launch. Format: tag1;tag2;build:12345-6. Tags should be separated by “;”. There are one special tag- build – it should be used for specification number of build for launch. |No |
|rp.batch.size.logs                             |In order to rise up performance and reduce number of requests to server |Yes |
|rp.keystore.resource                           |Put your JKS file into resources and specify path to it | |
|rp.keystore.password                           |Access password for JKS (certificate storage) package, mentioned above | |
|rp.convertimage                                |Colored log images can be converted to grayscale for reducing image size. Values: ‘true’ – will be converted. Any other value means false. |No |
|rp.mode                                        |Report portal provide possibility to specify visibility of executing launch. Currently supported two modes: DEFAULT  - all users from project can see this launch; DEBUG - only owner can see this launch(in debug sub tab). Note: for all java based clients(TestNg, Junit) mode will be set automaticaly to "DEFAULT" if it is not specified. |No |
|rp.skipped.issue                               |Report Portal provide feature to mark skipped tests as not 'To Investigate' items on WS side. Parameter could be equal boolean values: *TRUE* - skipped tests considered as issues and will be mark as 'To Investigate' on portal. *FALSE* - skipped tests will not be mark as 'To Investigate' on portal. |No |


Launch name can be edited once, and should be edited once, before first
execution. As usual, parts of launches are fixed for a long time. Keeping the
same name for launch, here we will understand a fixed list of suites under
launch, will help to have a history trend, and on UI instances of the same
launch will be saved with postfix "\#number", like "Test Launch \#1", "Test
Launch \#2" etc.

>   If mandatory properties are missed client throw exception
>   IllegalArgumentException.

**Proxy configuration**

The client uses standard java proxy mechanism. If you are new try [Java networking and proxies](<http://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html>) page.

Ways to set up properties:

a. reportportal.properties file
b. command line properties (-Dhttps.proxyHost=localhost)

**How to provide parameters**

There are two way to load parameters.

- Load from properties file

Properties file should have name: “reportportal.properties”. Properties file can
be situated on the class path (in the project directory).

If listener can’t find properties file it throws FileNotFoundException. By
default “reportportal.properties” exists in the reportportall-client.jar, but
user can create his own “reportportal.properties” file and put in class path.

- Use system variables

**Parameters loading order**

Client loads properties in the next order. Every next level overrides previous:

a.  Properties file. Client loads all known to him properties (specified in the
    "Input Parameters" section) from "reportportal.properties" file.

b.  Environment variables. If environment variables with names specified in the
    "Input Parameters" section exist client reload existing properties from
    environment variables.

c.  JVM variables. If JVM variables with names specified in the
    "Input Parameters" section exist, client overrides existing ones from
    JVM variables.

#### Events

* URI - saves story URI to be sent to ReportPortal afterwards
* Feature - notifies ReportPortal that some feature has started
* startOfScenarioLifeCycle - Runs before-* hooks for the scenario
* scenario - notifies ReportPortal that scenario has been finished
* Before - used to finish previously started hook
* step - collects step to be sent to ReportPortal afterwards
* Examples - generates data being sent to ReportPortal to report examples table
* background - notifies ReportPortal that before-* hooks have been finished
* Match - notify ReportPortal that next step of current scenario is started 
* Result - notify ReportPortal that step has finished and after-* hooks should be started (if any)
* After - notify ReportPortal that after-* hook has finished 	
* Embedding/Write - sends log to ReportPortal
* endOfScenarioLifeCycle - notifies ReportPortal that after-* hooks of scenario has been finished 
* eof - notifies ReportPortal that test feature execution is finished
* done - IS NOT processed by ReportPortal agent
* close - notifies ReportPortal that test execution is finished
* SyntaxError - IS NOT processed by ReportPortal agent 
* ScenarioOutline - IS NOT processed by ReportPortal agent
