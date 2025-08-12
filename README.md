# reactive Project

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using: 
```shell script
./mvnw package -Pnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/reactive-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.

## Related Guides

- RESTEasy Reactive ([guide](https://quarkus.io/guides/resteasy-reactive)): Reactive implementation of JAX-RS with additional features. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.

## Provided Code

### RESTEasy Reactive

Easily start your Reactive RESTful Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)

# Development mode
To run the application in development mode, you can use the following command:
```shell script
./mvnw compile quarkus:dev
```
This command will start the application in development mode, allowing you to make changes to the code and
see the changes reflected immediately without needing to restart the application.

# Running the application in dev mode with frontend
To run the application in development mode with the frontend, you can use the following command:
```shell script
./mvnw -Pfrontend quarkus:dev
```
This command will start the application in development mode with the frontend assets, allowing you to
make changes to the frontend code and see the changes reflected immediately without needing to restart the application.

# Running in production
## Building the application
To build the application for production, you can use the following command:
```shell script
./mvnw -Pfrontend clean package
```
This command will clean the project and package it for production, including the frontend assets.

##  Running the application
The only external component and requirement for our application is the database. If you don’t have
a PostgreSQL instance in your local environment, you can create one in a Docker container. Let’s try
this by running the following command:

```shell script
docker run -d --rm --name postgres -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:latest
```
To run the application, you can use the following command:
```shell script
java -Dquarkus.datasource.username=postgres -Dquarkus.datasource.password=pgpass -Dquarkus.datasource.reactive.url=postgresql://localhost:5432/postgres -Dquarkus.hibernate-orm.database.generation=create -jar target/quarkus-app/quarkus-run.jar
```
The application is available in a browser by navigating to http://localhost:8080.

## Running as native Build
To be able to run the application in native mode, we need to compile and package it first.
We need to execute the Maven clean package goals, but in this case, we’ll be using the frontend and
native Maven profiles combined:
```shell script
./mvnw -Pfrontend,native clean package
```
We can now execute the application by running the following command:
```shell script
./target/jahrbuch-generator-1.0.0-SNAPSHOT-runner  -Dquarkus.datasource.username=postgres -Dquarkus.datasource.password=pgpass -Dquarkus.datasource.reactive.url=postgresql://localhost:5432/postgres -Dquarkus.hibernate-orm.database.generation=create
```

# Deploying Your Application to Kubernetes
## Setting up a local cluster with minikube
To deploy your application to Kubernetes, you can use Minikube to set up a local cluster. First, ensure you have Minikube installed. You can start a Minikube cluster with the following command:

```shell script 
minikube start
```
we’ll need to enable minikube’s ingress
addon so that we can complete all of the tasks in the chapter. Let us enable this addon by executing
the following command:
```shell script
minikube addons enable ingress
```
## Creating a container image
### Creating an image repository in Docker Hub
If you don’t have a Docker Hub account, you’ll first have to sign up and create a new one by navigating
to https://hub.docker.com/. There’s a free personal plan that’s suitable for our needs and
just requires an email account.
### Building and pushing the container image to Docker Hub
Once you have a Docker Hub account, you can create a new repository by navigating to the
Repositories section and clicking on the Create Repository button. You can name your repository
`jahrbuch-generator` and set it to public or private, depending on your preference.
Once you have created your repository, you can push your container image to Docker Hub using the
following command:


Being able to build container images of our applications and push them to external registries is a very
valuable asset considering the new paradigm in systems administration and operations. Quarkus
provides several extensions to build and push container images and to create the required configuration
files to deploy them to Kubernetes. These extensions work great for some use cases; however, we’ll
be using Eclipse JKube and its Kubernetes Maven Plugin to build and deploy our application.
To use Eclipse JKube, you need to add the following dependency to your `pom.xml`:
```shell script
```xml
<plugin>
    <groupId>org.eclipse.jkube</groupId>
    <artifactId>kubernetes-maven-plugin</artifactId>
    <version>${jkube.version}</version>
</plugin>
```
Addg the following properties to the <properties> section in the pom.xml file:
```xml
<jkube.version>1.8.0</jkube.version>
<jkube.generator.name>drdboehm/jahrbuch-generator:latest</jkube.generator.name>
<jkube.image.name>jahrbuch-generator</jkube.image.name>
<jkube.image.tag>1.0.0</jkube.image.tag>
```
We can now proceed to build the container image for our application. However, we need to make
sure that the application is built and packaged first. Let us do this by running the following command
in a terminal:
To create a container image for your application, you can use the following command:
```shell script
./mvnw clean package -Pfrontend
```
The build should succeed and we should now be ready to create the container image by executing the
following command in the same terminal:
```shell script
./mvnw k8s:build
```
This command will build the container image using the Dockerfile generated by Eclipse JKube and
tag it with the name and version specified in the properties section of the `pom.xml` file

Creating a container image for the binary version of our application with
Eclipse JKube would be as easy as adding the -Pnative Maven flag to the commands we executed
in the previous code blocks:
```shell script
./mvnw clean package k8s:build -Pfrontend,native
```

We have now built a container image for the jahrbuch-generator; however, it’s only available in our local
Docker registry. To be able to use it from our Kubernetes cluster, we need to push it to Docker Hub.
Let us now learn how to achieve this using Eclipse JKube’s Kubernetes Maven Plugin.

### Pushing the container image to Docker Hub
Since Docker Hub is a public registry, pushing to
a repository is password-protected to prevent someone else from pushing an image to your repositories.
Eclipse JKube has several ways to retrieve the required credentials to perform the push; the easiest is
by reusing the ones stored in the local Docker configuration.
log in to Docker Hub from the command line by running the following command:
```shell script
docker login
```
We’re now ready to push the image to Docker Hub. Let us do this by executing the following command
in the same terminal we’ve been using until now:
```shell script
./mvnw k8s:push
```   
However, we’ve relied on a local Docker daemon to perform the build and push tasks.
There might be some environments, such as a continuous integration (CI) pipeline, where we don’t
have a Docker daemon available. Let us now see how to perform the same build and push tasks with
Eclipse JKube’s Kubernetes Maven Plugin but relying on Jib instead.

### Building and pushing the container image with Jib
Jib is a tool that allows you to build container images without requiring a Docker daemon. It
integrates with Maven and Gradle, and it can build images directly from your project’s sourcecode.
One of the advantages of Eclipse JKube when compared to
other alternatives is that you can switch the image build strategy just by providing a configuration flag.
Let us try to repeat the build and push process using Jib instead of Docker by running the Maven
command with the following configuration flags:

```shell script
./mvnw clean package k8s:build k8s:push -DskipTests -Djkube.build.strategy=jib -Djkube.docker.push.username=drdboehm -Djkube.docker.push.password=$password
```
This command will build the container image using Jib and push it to Docker Hub. Make sure to replace `$password` with your actual Docker Hub password.
```shell script
export password=your_password_here
```
You can also set the password in the `pom.xml` file under the `<properties>` section:
```xml
<jkube.docker.push.password>your_password_here</jkube.docker.push.password>
``` 
Now that we have our container image pushed to Docker Hub, we can proceed to deploy it to our Kubernetes cluster.

## Deploying the application to Kubernetes
### Adjusting the cluster manifests from the project’s pom.xml
The Eclipse JKube Kubernetes Maven Plugin generates the required Kubernetes manifests for our application based on the configuration in the `pom.xml` file. We can adjust these manifests to
suit our needs. For example, we can change the image name and tag in the `pom.xml` file:
```xml
<groupId>de.jamsintown</groupId>
<artifactId>jahrbuch-generator</artifactId>
<properties>
    <jkube.image.name>jahrbuch-generator</jkube.image.name>
    <jkube.image.tag>1.0.0</jkube.image.tag>
    <jkube.createExternalUrls>true</jkube.createExternalUrls>
    <jkube.domain>192.168.49.2.nip.io</jkube.domain>
    <postgresql.serviceName>postgresql</postgresql.serviceName>
    <jkube.enricher.jkube-project-label-group>${project.artifactId}</jkube.enricher.jkube-project-label-group>
</properties>
```
jkube.domain: A property that will be used as a host suffix for the generated Ingress.
In this case, the resulting URL where our application will be available is http://jahrbuch-generator.192.168.49.2.nip.io. You should replace the IP address in the property value
(192.168.49.2) with the output of executing minikube ip, which reveals minikube’s
public IP address. nip.io is a free service that provides DNS services to expose any IP address,
even private ones, as a hostname. You can learn more about this service at https://nip.io.

### Creating the cluster configuration manifests with Eclipse JKube
Eclipse JKube can generate the required Kubernetes manifests for our application based on the configuration in the `pom.xml` file. To create the cluster configuration manifests, you can run the following command:
```shell script
./mvnw k8s:resource
``` 



