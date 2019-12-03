# Minio Notifications manager

This components is able to handle minio notification registrations and message routing.
Minio supports many different brokers, but sends every single event notification to the very same topic, independently of the bucket. While it is possible to register many different brokers, with different destination topics, and handle all the routing at the event level by specifying the SQS ARN, this approach requires the intervention of a system administrator to create the ARNs.

By using the notifications manager instead, authorized users can register a specific kind of event, as supported by Minio, and receive a destination topic which will deliver only the notifications related to the very same event.
Each event is thus routed to a different topic.

The idea is to leverage the routing capabilities of this component to dispatch events to only the interested parties, i.e. those who have registered their interest in a specific kind of event via the API.

## Events definition

An event defined in the notifications manager represents an event occuring in Minio.
An event in Minio is related to an action performed on an object inside a bucket.

The fields available to define an ``event`` are grouped in two logical blocks.
First we need to identify the objects which will produce the even via:
* ``bucket``: the bucket containing the objects
* ``prefix``: an object prefix (includes the path)
* ``suffix``: an object suffix

Then we need to define the actions performed on such objects from the list:
*  ``s3:ObjectCreated:*``
*  ``s3:ObjectCreated:Put``
*  ``s3:ObjectCreated:Post``
*  ``s3:ObjectCreated:Copy``
*  ``s3:ObjectCreated:CompleteMultipartUpload``
*  ``s3:ObjectAccessed:Get``
*  ``s3:ObjectAccessed:Head``
*  ``s3:ObjectAccessed:*``
*  ``s3:ObjectRemoved:*``
*  ``s3:ObjectRemoved:Delete``
*  ``s3:ObjectRemoved:DeleteMarkerCreated``
*  ``s3:ReducedRedundancyLostObject``

An event can thus be built by specific all the information, for example:

```
{
   "bucket": "my-test-bucket",
   "actions" : [
      "s3:ObjectCreated:*",
      "s3:ObjectRemoved:*",
      "s3:ObjectAccessed:*"
   ],
   "prefix": "test/objects/prefix",
   "suffix": ".txt"
}

```

which will produce a message flow inside the topic ``minio/notifications/test/ev-4659d9507ce8bd34cf93dfc0643992cd``

## API

The REST Api exposed supports the following actions:

* ``GET /api/events/{bucket}`` to list all the events registered for the given bucket
* ``POST /api/events/{bucket}`` to register a new event for the given bucket
* ``GET /api/events/{bucket}/{id}`` to get the details of a specific event for the given bucket
* ``DELETE /api/events/{bucket}/{id}`` to remove a specific event for the given bucket

Furthermore, it is possible to list all the action types supported by calling :

* ``GET /api/event-types`` 


For additional information look at the included OpenAPI at ``http://localhost:8080/swagger_ui.html``.

## Configuration
All the configuration can be performed via *properties files* (even as yaml) or via *environmental variables*.

### Minio client
In order to set up the component it is necessary to provide the connection details for **Minio**.
The account needs to have either administrative privileges or an associated policy which enables the handling of notifications over any desired bucket.

```
minio.endpoint=${MINIO_ENDPOINT:}
minio.port=${MINIO_PORT:9000}
minio.secure=${MINIO_SECURE:false}
minio.region=${MINIO_REGION:}
minio.accessKey=${MINIO_ACCESS_KEY:}
minio.secretKey=${MINIO_SECRET_KEY:}
minio.queue=${MINIO_QUEUE:}
```
The ``queue`` property must contain the exact SQS ARN as provided by Minio.

### MQTT client
To enable the message routing for MQTT provide the required connection details.

```
mqtt.enable=${MQTT_ENABLE:true}
mqtt.broker=${MQTT_BROKER:}
mqtt.username=${MQTT_USERNAME:}
mqtt.password=${MQTT_PASSWORD:}
mqtt.identity=${MQTT_IDENTITY:}
mqtt.topic=${MQTT_TOPIC:}
mqtt.qos=${MQTT_QOS:2}
```

The ``topic`` property must match the one configured as destination topic inside minio configuration, for the SQS ARN previously selected.

### Authentication
The component can be safely deployed in a controlled environment without requiring client authentication.
When needed, administrators can configure *basic authentication* by setting the necessary parameters.

```
auth.enabled=true
auth.username=${ADMIN_USERNAME:admin}
auth.password=${ADMIN_PASSWORD:} 
```

The default configuration activates authentication with a randomly generated password, which is generated at startup and printed to STDOUT for development purposes. Please configure a secure account when needed.

### Manager <> Minio synchronization
By default, the local database holds only the locally registered events, which are propagated to Minio when needed.
If requested, the system can perform at startup a *one-way* or *two-way* synchronization, which will ensure that the notifications manager and Minio possess the same event definitions.

```
startup.sync.halt=${SYNC_HALT_ON_ERRORS:false}
startup.sync.import.enable=${SYNC_IMPORT:true}
startup.sync.import.clear=${SYNC_IMPORT_CLEAR:false}
startup.sync.export.enable=${SYNC_EXPORT:false}
startup.sync.export.clear=${SYNC_EXPORT_CLEAR:false}
```

By configuring ``import`` properties the manager will register a local proxy for any event found in Minio, for any bucket, which matches the SQS ARN specified in the configuration. If the ``clear`` is set to true, any local event not found in Minio will be erased.

By configuring ``export`` properties the manager will export all the locally registered event definitions to Minio, when possible. If locally registered buckets are missing, the export will ignore them. If the ``clear`` flag is set to true, any event found in Minio matching the given SQS ARN and not registered on the manager will be removed.

 
### Log level
To modify the *log level* update the property either via file or via ENV
```
logging.level.it.smartcommunitylab.minionotificationsmanager=${LOG_LEVEL:INFO}
```

### Database
All the event registrations are persisted inside a persistent database.
By default the system will leverage an embedded H2 database, a solution suitable for development or small scale deployments. If required, configure an external database by updating the configuration

```
spring.datasource.url=${JDBC_URL:jdbc:h2:file:./data/db}
spring.datasource.driverClassName=${JDBC_DRIVER:org.h2.Driver}
spring.datasource.username=${JDBC_USER:sa}
spring.datasource.password=${JDBC_PASS:password}
spring.jpa.database-platform=${JDBC_DIALECT:org.hibernate.dialect.H2Dialect}
```

Supported RDBMS are:
* embedded H2
* MySQL 5.5+
* PostgreSQL 9+

The system won't perform any kind of migration, so switching from one datasource to another is a disruptive operation. If required, perform an external backup/restore **before** switching configurations.
 

