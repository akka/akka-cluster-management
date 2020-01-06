# Dynamic Log Levels

Dynamic Log Levels for Logback hooks into Akka Management and provides a route where log levels can be read and set over HTTP.

## Project Info

@@project-info{ projectId="loglevels-logback" }

Requires @ref:[Akka Management](akka-management.md) and that the application uses [Logback](http://logback.qos.ch) as logging backend.

@@dependency[sbt,Gradle,Maven] {
  group=com.lightbend.akka.management
  artifact=loglevel-logback_$scala.binary_version$
  version=$project.version$
  group2=com.lightbend.akka.management
  artifact2=akka-management_$scala.binary.version$
  version2=$project.version$
}

With Akka Management started and this module on the classpath the module is automatically picked up and provides the following two HTTP routes:

### Reading Logger Levels

A HTTP `GET` request to `loglevel/logback?logger=[logger name]` will return the log level of that logger.

### Changing Logger Levels

Only enabled if `akka.management.http.route-providers-read-only` is set to `false`. 

@@@ warning

If enabling this make sure to properly secure your endpoint with HTTPS and authentication or else anyone with access to the system could change logger levels and potentially do a DoS attack by setting all loggers to `TRACE`.

@@@

A HTTP `PUT` request to `loglevel/logback?logger=[logger name]&level=[level name]` will change the level of that logger on the JVM the `ActorSystem` runs on.

For example using curl:

```
curl -X PUT "http://127.0.0.1:8558/loglevel/logback?logger=com.example.MyActor&level=DEBUG"
```

#### Classic and Internal Akka Logger Level

Internal Akka actors and classic Akka does logging through the built in API there is an [additional level of filtering](https://doc.akka.io/docs/akka/current/logging.html#slf4j) using the
`akka.loglevel` setting. If you have not set `akka.loglevel` to `DEBUG` (recommended) log entries from the classic logging API may never reach the logger backend at all.

The current level configured with `akka.loglevel` can be inspected with a GET request to `loglevel/akka`.

If management `read-only` is set to `false` PUT requests to `loglevel/akka?level=[level name]` will dynamically change that.
Note that the allowed level for Akka Classic logging is a subset of the loglevels supported by SLF4j, valid values are `OFF`, `DEBUG`, `INFO`, `WARNING` and `ERROR`.

For example using curl:

```
curl -X PUT "http://127.0.0.1:8558/loglevel/akka?level=DEBUG"
```
