Backplane README
================

The Backplane Server implements the [backplane-core-v1.1](https://sites.google.com/site/backplanespec/documentation/backplanespecification1-1#backplane.server.api) and [backplane-core-1.2](https://sites.google.com/site/backplanespec/documentation/backplane1-2) specifications, with the configuration and deployment specifics detailed in this document.

The implementation is backwards-compatible with [v1](http://www.backplaneworkinggroup.org/documentation/backplanespecification#backplane.server.api) of the protocol.

The deployment environment is Amazon AWS and the storage engine used is SimpleDB.

Configuration
-------------

Each Backplane Server logical instance is identified by its backplane-instance name.
SimpleDB is used for keeping Backplane Server's configuration data.

The Backplane Server learns its bootstrap configuration parameters from the following environment variables:

* `PARAM1`: The logical Backplane instance ID. Used to identify configuration data sets within SimpleDB and allow multiple (AWS) instances to be part of the same logical Backplane Server deployment.
* `AWS_ACCESS_KEY_ID`: Key ID for accessing SimpleDB.
* `AWS_SECRET_KEY`: Secret key for accessing SimpleDB.
* `PARAM2`: Reporting domain for emails sent from Backplane server via log4j's SMTPAppender (e.g., "samplebackplane.com").  Emails have a complete origin format of `${PARAM1}@${PARAM2}`.
* `PARAM3`: Destination for log4j emails (e.g., "backplane-email@homedomain.com").

Build Server
------------

This project requires the [Maven](http://maven.apache.org/guides/getting-started/maven-in-five-minutes.html) build tool.

`mvn package -DskipTests=true`

To process the tests, you must have created an Amazon SimpleDB instance.  Use the following modification to the Maven build command (be sure to add your parameters):

`mvn package -DargLine="-DPARAM1=instanceid -DPARAM2=sample.com -DPARAM3=quiet@sample.com -DAWS_ACCESS_KEY_ID=key -DAWS_SECRET_KEY=secret"`

Maven will create a WAR file for deployment in the server/target directory.

Administrator Authentication
----------------------------

Administrators can access the Bus Provisioning API and update Bus Configuration data. Administrator credentials are kept in the `<backplane-instance>_Admin` table:

* `USER`: admin username
* `PWDHASH`: admin password (hash)

To retrieve the generated metrics, you must add the `<backplane-instance>__bpMetricAuth` table:

* `USER`: metrics username
* `PWDHASH`: metrics password (hash)

These Backplane admin users are provisioned manually.  To generate a password hash, see the HmacHashUtils class and its main() method.

Bus Configuration
-----------------

A (customer) bus is defined by the following Bus Configuration data, kept in the `<backplane-instance>_BusConfig` table:

* `BUS_NAME`: bus name
* `RETENTION_TIME_SECONDS`: message retention time (seconds)
* `RETENTION_STICKY_TIME_SECONDS`: message retention time for sticky messages (seconds)
* `<backplane_user>:` comma-separated list of permissions

### Backplane Bus Access Accounts

Backplane user accounts are kept in the `<backplane-instance>_User` table:

* `USER`: backplane username
* `PWDHASH`: backplane password (hash)

### Backplane Bus Permissions

The initial set of permissions is:

* `GETALL`: allows access to the "Get All" API endpoint
* `POST`: allows posting of (common) messages to the bus
* `IDENTITY`: allows posting of identity specific messages to the bus

Note: An access decision for the "Post" operation requires the message type (from which the required permission can be determined), in addition to the endpoint (bus and channel) and credentials required for the "Get All" operation.

Endpoints
---------

All endpoints are available at paths starting with all of the following prefixes:

* /v1/
* /v1.1/
* /v1.2/

Backplane Message Store
-----------------------

Backplane message frames are stored in SimpleDB under the `<backplane-instance>_messages` table:

* message_id
* channel_name
* message_payload

Backplane API
-------------

The Backplane Server exposes the following Backplane API endpoints, as defined by the [backplane-core] specification in [Section 4.4](http://www.google.com/url?q=http%3A%2F%2Fwww.backplaneworkinggroup.org%2Fdocumentation%2Fbackplanespecification%23backplane.server.api&sa=D&sntz=1&usg=AFrqEze0Sq9K21KxH_dC7ekUpFCLNGXZaQ).

### Get All (v1.1)

* Endpoint:  `/v1.1/bus/<BUS_NAME>`
* Security: HTTPS GET, HTTP-Basic authentication
* Request parameters: since (optional)
* Response body: list of backplane frames

### Get All (v1.2)

* Endpoint:  `/v1.2/bus/<BUS_NAME>`
* Security: HTTPS GET, HTTP-Basic authentication
* Request parameters: since (optional)
* **Request parameters: sticky (optional)**
* Response body: list of backplane frames

### Get Channel (v1.1)

* Endpoint:  `/v1.1/bus/<BUS_NAME>/channel/<CHANNEL_NAME>`
* Security: HTTPS GET, no authentication
* Request parameters: since (optional)
* Response body: list of backplane frames

### Get Channel (v1.2)

* Endpoint:  `/v1.2/bus/<BUS_NAME>/channel/<CHANNEL_NAME>`
* Security: HTTPS GET, no authentication
* Request parameters: since (optional)
* **Request parameters: sticky (optional)**
* Response body: list of backplane frames

### Post

* Endpoint:  `/v1.1/bus/<BUS_NAME>/channel/<CHANNEL_NAME>`
* Security: HTTPS POST, HTTP-Basic authentication
* Request body: list of backplane messages
* Response body: empty on success or if the post exceeds the maximum number of message allowed (default of 100 per channel) an error is returned:

```json
{"ERR_MSG":"Message limit exceeded for this channel"}
```

This value may be overridden by placing an entry in the `<backplane-instance>_bpserverconfig` table called `DEFAULT_MESSAGES_MAX` with an appropriate value.


User Provisioning API
---------------------

Create or Update User Entry

Example HTTP API request:

POST `/v1.1/provision/user/update`
Host: backplanesample.com
Content-Type: application/json

```json
{
  "admin": "admin",
  "secret": "admin",
  "configs": [
 	{
    	"USER": "user1",
    	"PWDHASH": "user1"
 	},
 	{
    	"USER": "user2",
    	"PWDHASH": "user2"
 	}
  ]
}
```

Example curl command for the above HTTP API request:

`curl -i --data @json_data_file.txt -H "Content-type: application/json" http://backplanesample.com/v1.1/provision/user/update`


### List All Users

Example HTTP API request:

POST `/v1.1/provision/user/list`
Host: backplanesample.com
Content-Type: application/json

```json
{
  "admin": "admin",
  "secret": "admin",
  "entities": []
}
```

Example curl command for the above HTTP API request:

`curl -i --data @json_data_file.txt -H "Content-type: application/json" http://backplanesample.com/v1.1/provision/user/list`

### Delete User(s)

Example HTTP API request:

POST `/v1.1/provision/user/delete`
Host: backplanesample.com
`Content-Type: application/json`

```json
{
  "admin": "admin",
  "secret": "admin",
  "entities": [ "user1", "user2" ]
}
```

Example curl command for the above HTTP API request:

`curl -i --data @json_data_file.txt -H "Content-type: application/json" http://backplanesample.com/v1.1/provision/user/delete`



Bus Provisioning API
--------------------

All Bus Provisioning API calls are only available over HTTPS and are authenticated against the `<backplane-instance>_Admin` table.
### List Buses

Returns a list of Bus Configuration data sets.

Request:

* endpoint: `/v1.1/provision/bus/list`
* HTTP method: POST
* all entries are required
* the value for the entities entry can be empty, in which case all Backplane Bus configurations are returned
* body format:

```json
{
    "admin": "<adminUsername>",
    "secret": "<adminPassword>",
    "entities": [ "customer1.com", "customer2.net" ]
}
```

Response:

* status 200 for requests with valid format that were processed successfully
* requested bus configurations that were not found will result in a `"ERR_MSG": "NOT_FOUND"` entry
* requested bus configurations that encountered retrieval errors will result in a `"ERR_MSG": "<error_message>"` entry
* body format:

```json
{
    "customer1":
        {
            "BUS_NAME": "customer1",
            "RETENTION_TIME_SECONDS": "600",
            "<username1>: "<permission1>,<permission2>,...",
            "<username2>: "<permission3>,<permission4>,...",
            ...
        },
...
}
```

`curl -i --data @json_data_file.txt -H "Content-type: application/json" http://backplanesample.com/v1.1/provision/bus/list`

### Delete Bus

Removes the specified bus configuration(s).

Request:

* endpoint: `/v1.1/provision/bus/delete`
* HTTP method: POST
* all entries are required
* body format:

```json
{
    "admin": "<adminUsername>",
    "secret": "<adminPassword>",
    "entities": [ "customer1.com", "customer2.net" ]
}
```

Response:

* status 200 for requests with valid format that were processed successfully
* an entry is returned for each bus configuration with the status of the delete operation - either "`BACKPLANE_DELETE_SUCCESS`" or an error message if the deletion was not completed successfully
* body format:

```json
{
    "customer1.com": "BACKPLANE_DELETE_SUCCESS",
    "customer2.net": "<delete error message>"
}
```

`curl -i --data @json_data_file.txt -H "Content-type: application/json" http://backplanesample.com/v1.1/provision/bus/delete`

*NOTE:* Deleting a bus configuration removes all Privileged Access Level defined for it; it does *NOT* remove any existing Backplane Messages from any of its channels. They will still be available for retrieval through the "Get Channel Messages" Backplane operation until they expire.

### Update Buses

Updates Bus configuration data. If a bus in the provided list does not exist, it is added to the Bus Configuration table.

Request (v1.1):

* endpoint: `/v1/provision/bus/update`
* HTTP method: POST
* all entries are required
* provided Bus configurations must be in the valid format for the specified endpoint type
* body format:

```json
{
    "admin": "<adminUsername>",
    "secret": "<adminPassword>",
    "configs": [
        {
            "BUS_NAME": "customer1",
            "RETENTION_TIME_SECONDS": "600",
            "<username1>: "<permission1>,<permission2>,...",
            "<username2>: "<permission3>,<permission4>,...",
            ...
            },
            ...
    ]
}
```

Request (v1.2):

* endpoint: `/v1.2/provision/bus/update`
* HTTP method: POST
* all entries are required
* provided Bus configurations must be in the valid format for the specified endpoint type
* body format:

```json
{
    "admin": "<adminUsername>",
    "secret": "<adminPassword>",
    "configs": [
        {
            "BUS_NAME": "customer1",
            "RETENTION_TIME_SECONDS": "600",
            "RETENTION_STICKY_TIME_SECONDS": "28800",
            "<username1>: "<permission1>,<permission2>,...",
            "<username2>: "<permission3>,<permission4>,...",
            ...
            },
            ...
    ]
}
```

Response / success:

* status 200 for requests with valid format that were processed successfully
* an entry is returned for each bus configuration with the status of the update operation - either `"BACKPLANE_UPDATE_SUCCESS"` or an error message if the deletion was not completed successfully.
* body format:

```json
{
    "customer1.com": "BACKPLANE_UPDATE_SUCCESS",
    "customer2.net": "<update error message>"
}
```

### Retrieve Metrics

Backplane server publishes usages statistics.  Interesting data points include the number of channel gets, posts and how long it takes the server to retrieve a get payload.  The output will include one "instance" entry for each running Backplane server on a common SimpleDB instance (`PARAM1`).  Each server publishes its accumulated metrics every few minutes to SimpleDB in a maximum of one entry per running instance.  The server handling a metric request will retrieve all entries and compile them into one response.

If a server is restarted, it will create a new instance ID and begin reporting a fresh set of metrics.  The previous "orphaned" metric entry in SimpleDB for this server will be removed automatically.

Request:

* endpoint: `/v1/metrics/dump`
* HTTP method: POST
* all entries are required
* body format:

```json
{
    "user": "metrics",
    "secret": "someSecretKey"
}
```

Response success:

* status 200 for requests with valid format that were processed successfully
* body format (sample data, with metric details removed for brevity)

```json
[
   {
      "id":"6e3d41e0-2206-4c1e-93d2-42ed0d21e377",
      "time_collected":"2011-10-17T22:42:09.177Z",
      "metrics":{
      ...
      }
   },
      "id":"blah",
      "time_collected":"blah",
      "metrics":{
      ...
      }
   }
]
```

### Error Responses

status 401 Unauthorized - API authentication failure
status 400 Bad Request - invalid request format / syntax

Each error response body will contain an error message in the following format:

```json
{
    "ERR_MSG": "<message>"
}
```


