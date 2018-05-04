<a id="akka-management"></a>
# Akka Management

Akka Management is the central module of all the management utilities that pulls them all together.


The operations exposed are comparable to the Command Line Management tool or the JMX interface `akka-cluster` provides.

## Akka management architecture overview 

Akka management serves as the central piece that pulls together various Akka management extensions
and actually exposes them as simple HTTP endpoints. 

For example, the "Cluster HTTP Management" module exposes HTTP routes that can be used to monitor,
and even trigger joining/leaving/downing decisions via HTTP calls to these routes. The routes and
logic for these are implemented inside the `akka-management-cluster-http`, however that module serves
as a "plugin" to `akka-management` itself. 

Libraries (referred to as "management extensions") can contribute to the exposed HTTP routes by 
appending to the `akka.management.http.route-providers` list. The core `AkkaManagement` extension
then collects all the routes and serves them together under the management HTTP server. This is in order
to avoid having to start an additional HTTP server for each additional extension, and also, it allows
easy extension of routes served by including libraries that offer new capabilities (such as health-checks or
cluster information etc).

Management extensions whose nature is "active" (as in, they "do something proactively") should not be
started automatically, but instead be started manually by the user. One example of that is the Cluster
Bootstrap, which on one hand does contributes routes to Akka Management, however the bootstraping process
does not start unless `ClusterBootstrap().start()` is invoked, which allows the user to decide when exactly
it is ready and wants to start joining an existing cluster.

![project structure](images/structure.png)

## Dependencies

The main Akka Management dependency is called `akka-management`. By itself however it does not provide any capabilities,
and you have to combine it with the management extension libraries that you want to make use of (e.g. cluster http management,
or cluster bootstrap). This design choice was made to make users able to include only the minimal set of features that they 
actually want to use (and load) in their project.

@@dependency[sbt,Gradle,Maven] {
  group="com.lightbend.akka.management"
  artifact="akka-management_$scala.binaryVersion$"
  version="$version$"
}

And in addition to that, include all of the dependencies for the features you'd like to use,
like `akka-management-bootstrap` etc. Refer to each extensions documentation page to learn about how
to configure and use it.

## Basic usage

Remember that Akka Management does not start automatically and the routes will only be exposed once you trigger:

Scala
:   ```scala
    AkkaManagement(system).start()
    ```

Java
:   ```java
    AkkaManagement.get(system).start();
    ```
    
This is in order to allow users to prepare anything else they might want to prepare or start before exposing routes for 
the bootstrap joining process and other purposes.


## Basic Configuration

You can configure hostname and port to use for the HTTP Cluster management by overriding the following:

```
akka.management.http.hostname = "127.0.0.1"
akka.management.http.port = 19999
```

Note that the default value for hostname is `InetAddress.getLocalHost.getHostAddress`, which may or may not evaluate to
`127.0.0.1`.

In case you are running multiple cluster instances within the same JVM these configuration parameters should allow you
to expose different cluster management APIs by modifying the port:

Scala
:   ```scala
    //Config Actor system 1
    akka.management.http.hostname = "127.0.0.1"
    akka.management.http.port = 19999
    ...
    //Config Actor system 2
    akka.management.http.hostname = "127.0.0.1"
    akka.management.http.port = 20000
    ...
    val actorSystem1 = ActorSystem("as1", config1)
    val actorSystem2 = ActorSystem("as2", config2)
    ...
    AkkaManagement(actorSystem1).start()
    AkkaManagement(actorSystem2).start()
    ```

Java
:   ```java
    //Config Actor system 1
    akka.management.http.hostname = "127.0.0.1"
    akka.management.http.port = 19999
    ...
    //Config Actor system 2
    akka.management.http.hostname = "127.0.0.1"
    akka.management.http.port = 20000
    ...
    ActorSystem actorSystem1 = ActorSystem.create("as1", config1);
    ActorSystem actorSystem2 = ActorSystem.create("as2", config2);
    ...
    AkkaManagement.get(actorSystem1).start();
    AkkaManagement.get(actorSystem2).start();
    ```

When running akka nodes behind NATs or inside docker containers in bridge mode, 
it's necessary to set different hostname and port number to bind for the HTTP Server for Http Cluster Management:

application.conf
:   ```hocon
  // Get hostname from environmental variable HOST
  akka.management.http.hostname = ${HOST} 
  akka.management.port = 19999
  // Get port from environmental variable PORT_19999 if it's defined, otherwise 19999
  akka.management.port = ${?PORT_19999} 
  akka.management.http.bind-hostname = 0.0.0.0
  akka.management.http.bind-port = 19999    
    ```  

It is also possible to modify the base path of the API, by setting the appropriate value in application.conf: 

application.conf
:   ```hocon
    akka.management.http.base-path = "myClusterName"
    ```

In this example, with this configuration, then the akka management routes will will be exposed at under the `/myClusterName/...`,
base path. For example, when using Akka Cluster Management routes the members information would then be available under
`/myClusterName/shards/{name}` etc.


## Configuring Security

@@@ note

HTTPS is not enabled by default as additional configuration from the developer is required This module does not provide security by default. It's the developer's choice to add security to this API.

@@@

The non-secured usage of the module is as follows:

Scala
:   ```scala
    AkkaManagement(system).start()
    ```

Java
:   ```java
    AkkaManagement.get(system).start();
    ```

### Enabling TLS/SSL (HTTPS) for Cluster HTTP Management

To enable SSL you need to provide an `SSLContext`. You can find more information about it in @extref[Server side https support](akka-http-docs:scala/http/server-side-https-support)

Scala
:   ```scala
    val https: HttpsConnectionContext = ConnectionContext.https(sslContext)
    //...
    val management = AkkaManagement(system)
    management.setHttpsContext(https)
    management.start()
    ```

Java
:   ```java
    HttpsConnectionContext https = ConnectionContext.https(sslContext);
    //...
    AkkaManagement management = AkkaManagement.get(system);
    management.setHttpsContext(https);
    management.start();
    ```
    
You can also refer to [AkkaManagementHttpEndpointSpec](https://github.com/akka/akka-management/blob/119ad1871c3907c2ca528720361b8ccb20234c55/management/src/test/scala/akka/management/http/AkkaManagementHttpEndpointSpec.scala#L124-L148) where a full example configuring the HTTPS context is shown.

### Enabling Basic Authentication

To enable Basic Authentication you need to provide an authenticator object before starting the management extension. 
You can find more information in @extref:[Authenticate Basic Async directive](akka-http-docs:scala/http/routing-dsl/directives/security-directives/authenticateBasicAsync)

Scala
:   ```scala
    def myUserPassAuthenticator(credentials: Credentials): Future[Option[String]] =
      credentials match {
        case p @ Credentials.Provided(id) ⇒
          Future {
            // potentially
            if (p.verify("p4ssw0rd")) Some(id)
            else None
          }
        case _ ⇒ Future.successful(None)
      }
    // ...
    val management = AkkaManagement(system)
    management.setAsyncAuthenticator(myUserPassAuthenticator)
    management.start()  
    ```

Java
:   ```java
    final Function<Optional<ProvidedCredentials>, CompletionStage<Optional<String>>> 
      myUserPassAuthenticator = opt -> {
        if (opt.filter(c -> (c != null) && c.verify("p4ssw0rd")).isPresent()) {
          return CompletableFuture.completedFuture(Optional.of(opt.get().identifier()));
        } else {
          return CompletableFuture.completedFuture(Optional.empty());
        }
      };
    // ... 
    management.setAsyncAuthenticator(myUserPassAuthenticator);
    management.start();
    ```

@@@ note
  You can combine the two security options in order to enable HTTPS as well as basic authentication. 
  In order to do this, invoke both `setAsyncAuthenticator` as well as `setHttpsContext` *before* calling `start()`.
@@@

## Stopping Akka Management

In a dynamic environment you might stop instances of Akka Management, for example if you don't want to free up resources
taken by the HTTP server serving the Management routes. 

You can do so by calling `stop()` on @scaladoc[AkkaManagement](akka.management.http.AkkaManagement). 
This method return a `Future[Done]` to inform when the server has been stopped.

Scala
:   ```scala
    val httpClusterManagement = AkkaManagement(system)
    httpClusterManagement.start()
    //...
    val bindingFuture = httpClusterManagement.stop()
    bindingFuture.onComplete { _ => println("It's stopped") }
    ```

Java
:   ```java
    AkkaManagement httpClusterManagement = AkkaManagement.create(system);
    httpClusterManagement.start();
    //...
    httpClusterManagement.stop();
    ```

