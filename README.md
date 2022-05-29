# pwp

This application was generated using [JHipster](https://jhipster.github.io).

## Documentation
This document will cover technical details such as setup, continous integration etc. In [the architecture document](doc/architecture.md) you 
will find a brief overview over the architecture. [The link collection](doc/useful_links.md) contains useful resources such as API references
and tutorials.

An API documentation (swagger) is created automatically in dev profile and is available after startup at [Swagger Page](http://localhost:8080/swagger-ui/).

An API documentation (swagger) is created automatically in dev profile and is available after startup at [Swagger Page](http://localhost:8080/swagger-ui.html).

## Setup

This project is using `node`, `yarn` and `webpack` to build the frontend.
These tools are made available with the [Frontend Maven Plugin](https://github.com/eirslett/frontend-maven-plugin).

### Checkout and import
Checkout the project from GIT using the command-line:
git clone https://<user>@bitbucket.org/wealthpilot/wealthpilot.git

Then open the project in IntelliJ, using the "Open Project" command (not "Import").

Once the project has finished loading, open the Project Module settings and 
* rename the module to `pwp` 
* add your JDK location.

You should now be able to run the Project from IntelliJ.

### Installing Build Tools

#### Installing via Maven

##### Install Maven
* On macOS, you install [Homebrew](http://brew.sh) first. Then install Maven via `brew install maven`.
* Windows: tbd
* Linux: install via the distribution tools, ie. `apt-get`, `rpm`, etc.
You then can use the `mvn` command on Mac, Windows or Linux.

##### Front end installation with Maven
This version is the preferred one as it uses a well-defined version of `node` and `yarn`. Be aware that using other 
globally installed versions of `yarn` may generate a different `yarn.lock` file!

`Node` and `yarn` packages can be installed from `maven` (using the `node` and `yarn` versions defined in `pom.xml` for better reproducibility).
The following command installs the `node` and `yarn` binaries inside a `node` directory.

    mvn frontend:install-node-and-yarn
    
Call `yarn install` using the pre-installed `yarn`

    mvn frontend:yarn  
  
Examples of passing parameters to pre-installed frontend binaries

    mvn frontend:yarn -Dfrontend.yarn.arguments="add -D eslint-loader"
    node/yarn/dist/bin/yarn add -D eslint-loader

#### Global installation 

Using a globally installed node/yarn/webpack is discouraged as version differences may 
produce different behaviour on production system and on your local system. It is heavily
recommended using the **frontend tools of maven** instead!

If and only if the maven frontend tools can't be used, you'd use the globally installed binaries of
the frontend tools.

1.  [Node.js]: We use Node as base to run other development tools.
    Depending on your system, you can install Node either from source or as a pre-packaged bundle.
    
    * MacOS:
         Run `brew install node`
    * Windows: 
         Download and run the setup from the [official NodeJS website](https://nodejs.org/en/download/)
        
2. [Yarn]: Yarn is used as package manager. It is faster and has better resolutions than npm.    

    * macOS:
        Run `brew install yarn`
    * Windows:
        Download from the internet.

3.  Now you should be able to run the following command to install development tools. You will only need to re-run this command when dependencies change in `package.json`.

        yarn install
    
3.  We use [Webpack] as our bundler. Install the `webpack` command-line tool globally with:

        yarn global add webpack@4.44.1 webpack-cli@4.2.0
        
It is recommended to use _Option 1_ of the instructions as it is the easiest, fastest and minimally intrusive way.     

## Code Formatting

Common code formatting is ensured by shared Intellij settings and Checkstyle.

### Intellij Settings

The common settings for the Intellij code formatting are located in the 
[intellij-code-style-formatter.xml](intellij-code-style-formatter.xml) file.

To apply these settings in your IDE, you must:
1.  Open the Settings
2.  Go to Editor > Code Style
3.  Click the **Cog** icon to the right of the _Scheme_ dropdown and choose _Import Scheme > Intellij IDEA code style xml_
4.  Select the file intellij-code-style-formatter.xml file
5.  Choose the target _Scheme_ you want to import the settings to and click **Ok**

### Checkstyle 
Checkstyle verifies that the rules specified in the [checkstyle.xml](checkstyle.xml) configuration file are followed. 

You can execute a Checkstyle run via Maven as part of the reporting by running `mvn site` 
or as standalone execution with `mvn checkstyle:checkstyle`.

## Development Tips

1.  Run the following commands in two separate terminals to create a blissful development experience where your browser
    auto-refreshes when files change on your hard drive.
    
        mvn
        mvn frontend:yarn -Dfrontend.yarn.arguments="start"
        
2. Maybe better is even to start the two Run Configurations from IntelliJ:
        
      * WP Default
      * WP Frontend Dev Server  
      
3. Frontend development then should be done using port [9000](http://localhost:9000) and for testing tenants or just backend development (without hot reload),
the port [8080](http://localhost:8080) may be used.

## Unexpected compilation errors

If you get compilation errors in the generated code (`target` folder), run the following command (in the project root directory):

`mvn clean test-compile`

This should compile the app successfully again.

Note: For the `mvn` command to work, [maven](https://maven.apache.org/download.cgi) has to be installed.

For `Windows` you may want to follow [this](https://www.mkyong.com/maven/how-to-install-maven-in-windows/) guide to do so.

For `macOS` you may install it using [Homebrew](https://brew.sh/index_de).

## Building for production

### Docker

For a production build to succeed, Docker must be installed. On `macOS` and `Windows` you should register on [Docker Hub](https://hub.docker.com)
and download and install the Docker executable. `Send usage statistics` must be deactivated.

On Windows, we need to activate "Expose daemon on `tcp://localhost:2375` without TLS", as we use a legacy build.

### Build

To optimize the pwp client for production, run:

    mvn -DskipITs -DskipTests -Pprod clean package dockerfile:build -Ddockerfile.tag=master

This will concatenate and minify `CSS` and `JavaScript` files. It will also modify `index.html` so it references
these new files.

### Run

To ensure everything worked, run:

    java -jar target/*.war --spring.profiles.active=prod

Then navigate to [http://localhost:8080](http://localhost:8080) in your browser.

## Testing

Unit tests are run by [Jest]. They're located in `app` and can be run with:

	yarn test
	

## Continuous Integration

To setup this project in Jenkins, use the following configuration:

* Project name: `pwp`
* Source Code Management
    * Git Repository: `git@github.com:xxxx/pwp.git`
    * Branches to build: `*/master`
    * Additional Behaviours: `Wipe out repository & force clone`
* Build Triggers
    * Poll SCM / Schedule: `H/5 * * * *`
* Build
    * Invoke Maven / Tasks: `-Pprod clean package`
    * Execute Shell / Command:
        ````
        mvn spring-boot:run &
        bootPid=$!
        sleep 30s
        kill $bootPid
        ````
* Post-build Actions
    * Publish JUnit test result report / Test Report XMLs: `build/test-results/*.xml,build/reports/e2e/*.xml`
    * Publish Jest test result to Jenkins and Sonar

[JHipster]: https://jhipster.github.io/
[Node.js]: https://nodejs.org/
[Yarn]: https://yarnpkg.com/
[Webpack]: http://webpack.js.org/
[Jest]: http://www.jestjs.io/


## IntelliJ Setup

### Project Setup

* Open the project (Do not import)
* Reimport Maven Project
* Mark some directories as excluded (for faster parsing/searching):
  * ```node```
  * ```node_modules```
  * ```target```

#### JPA/Hibernate Support

##### JQL Queries Syntax Check

For JQL support in IntelliJ, add Hibernate and JPA to 

* File/Project Structure/Modules
* File/Project Structure/Facets

Set JPA/Default JPA Provider to "Hibernate".

##### Entities Columns Check

Create database connection in Database View, add jdbc connection to projects database.

Open Persistence View (View/Tool Window/Persistence).
Right click on project, "Assign Data Sources" and assign the created data source.

### Build

The project generates some sources - this must be done manually via menu Build/Rebuild Project.
The source is generated in ```target/generated_sources```.

### Plugins

Mandatory

* Checkstyle-IDEA: execute checkstyle rules
* Lombok

Optional (Helpful)

* .ignore: syntax highlighting for ignore
* BashSupport: for shell script files 

### Database Connection H2 Development

If you want to access the project's database from within IntelliJ, add the data source as follows, it is recommended to start the application first,
then wait until the database is set up and stop the application before adding the database to IntelliJ:

Database Tool/Add (plus sign)/Data Source/H2 and set the following properties:
* Name: ```wealthpilot h2```
* User: ```pwp```
* Password:  (no password)
* URL Only: ```jdbc:h2:file:<absolute_path_to_project>/wealthpilot/target/h2-db/pwp;AUTO_SERVER=TRUE```
* Tx: ```manual``` (optional, as you like)

On `macOS` first try the configurations above. If it's not working, there is a known bug regarding the system wide Keychain as IntelliJ drops the password and sends an empty string when it cannot
"remember the password". As a workaround either set "password save" to "never" or explicitly open the keychain using `Keychain Access.app` in the
Utilities folder.  

For the URL on Windows you must use a format like `C:/...` with forward slashes, as backslashes lead to errors. 

### Sending emails from the local DEV environment

In the (local) DEV environment, a JHipster property is enabled which "catches" all emails and sends them to `application-testmails@wealthpilot.de`.
To override this and make the application send the emails elsewhere, an environment variable must be set: `MAIL_ALLMAILSRECEIVER`.

## Setting the environment variable on Windows

* Open the windows search and type "Environment", the select "Edit environment variables for your account"
* Under "User variables for <user-name>", select the "New..." button
* In the dialog window, enter "MAIL_ALLMAILSRECEIVER" in the field "Variable name:"
* Enter your preferred email address in the field "Variable value:"
* Click OK to close the "new" dialog, then OK to close the "Environment variables" dialog 

## Setting the environment variable on macOS

For setting a persistent environment variable for the GUI, a shell script which is run by a LaunchAgent can be used.
A file containing code like the following should be provided in the folder `~/Library/LaunchAgents`. You must name the file `setenv.wealthpilot.plist`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist>
	<dict>
		<key>KeepAlive</key>
		<dict>
			<key>SuccessfulExit</key>
			<false/>
		</dict>
		<key>Label</key>
		<string>setenv.wealthpilot</string>
		<key>ProgramArguments</key>
		<array>
			<string>sh</string>
			<string>-c</string>
			<string>launchctl setenv MAIL_ALLMAILSRECEIVER email@domain</string>
		</array>
		<key>RunAtLoad</key>
		<true/>
	</dict>
</plist>
```

You can check, if the environment variable was set correctly by following steps:
* login into the local frontend application. (Run `WP Default` profile of the application)
* in the upper right corner click the dropdown menu `Admin --> Konfiguration`
* under setting `mail` you will find the json attribute `"allMailsReceiver":` or you can just search for that attribute on the page
* the value of this attribute should be your email, which you set  in the plist file instead of `email@domain`

If it is not set, try following solutions:
* `mvn clean install`
* restart IntelliJ
* use following command in terminal to load the plist file:
    + `launchctl load /Users/<USER>/Library/LaunchAgents/setenv.wealthpilot.plist`
* restart computer
