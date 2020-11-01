---
title: How to make a quick'n'dirty CDC with PostgreSQL
date: 2020-10-16
---

# How to make a quick'n'dirty CDC with PostgreSQL

## Overview
At `${WORK}`, it's been several time that I encountered technical architectures that rely on quite powerful tools that emit all the events that happen to a Database. This tools belong to a category named Change Data Capture. 

Among multiple usage, here are the three that I encountered : 
* *Data Offload*: 
* *Outbox Pattern*:

Multiple tools exists: 
* Debezium (de facto)


## What are we going to do?

I wanted to take some time to understand how exactly this kind of tools work. And what's better for that than building our own?

Thus, we're going to build a small Scala library which will listen to PostgreSQL changes. 

For that, we're going to rely on a few libraries: `fs2`, `scodec` and `shapeless`, and everything in a `cats`-friendly way. 


## But wait... How does it work?

### A little bit of theory, first...
#### In general

Coucou dd 


#### Postgres specific
Since v10, Postgres allow us to...


## Let's play with PostgreSQL

### Start a new Database Cluster

Okay, let's get started! First, let's write 3 small `make` targets to run a PostgreSQL instance locally: 

{{< highlight make >}}

PGDATA := postgres

# https://github.com/postgres/postgres/blob/2b27273435392d1606f0ffc95d73a439a457f08e/src/backend/replication/pgoutput/pgoutput.c#L123
$(PGDATA)/PG_VERSION:
	initdb \
		-D "$(PGDATA)" \
		-A "trust" \
		-U "postgres"

.PHONY: pg-start
pg-start: $(PGDATA)/PG_VERSION
	postgres \
		-D "$(PGDATA)" \
		-c unix_socket_directories="$(PWD)" \
		-c wal_level="logical" \
		-c max_replication_slots=1

{{< / highlight >}}

A few words here: 
* In order to be able to use PostgreSQL's Logical Replication, we have to setup the `wal_level` to `logical` in order to let the database export and in our case here;
* We're setting the `max_replication_slots` to `1` as there will be only one slot that is going to be used. 

Using this, we can now start PostgreSQL by running `make pg-start. 

### Create a table

We're going to create a `persons` table and listen to its changes. Quite easy: 

{{< highlight sql >}}

CREATE TABLE
    persons (
        id SERIAL PRIMARY KEY,
        first_name TEXT, 
        last_name TEXT
    );

ALTER TABLE persons REPLICA IDENTITY FULL;

{{< / highlight >}}

See the `REPLICA IDENTITY` property? It control the [behavior of the Logical Replication](https://www.postgresql.org/docs/13/sql-altertable.html#SQL-CREATETABLE-REPLICA-IDENTITY) of the table. By using the `FULL` value, we say to PostgreSQL that we want it to emit the old row enirely in case of `UPDATE` or `DELETE` statements.

### Insert some rows
We're going to use a sample files (named `persons.txt`... Original, right?) to fill our table using the `pg-populate-target`: 

{{< highlight bash >}}

[//]: # <<< pg-populate-target >>>

{{< / highlight >}}

### Listen for changes
Okay! Now we have a running PostgreSQL instance and a fake workload which produce rows inside our `persons` table. We can now listen for the changes! For this, we can use the `pg_recvlogical` tool. 

{{< highlight bash >}}

[//]: # <<< pg-populate-target >>>

{{< / highlight >}}

Great! We see that changes in the `persons` table are publicated and we have a way to receive them using `pg_recvlogical` standard output. But it's a binary stream and we need to decode it in some way in order to use it programmatically. 


## Decoding `pg_recvlogical` standard output 

### Sample creation

When you take a look at `pg_recvlogical`'s output, you can see that there for each change, there is a new line. This is actually false (as there is no such thing as a line abstraction in binary format), but we're going to ignore that for now


### The `CaptureSpec` abstract class



### Protocol decoding with the `scodec` library

#### Model definition

The protocol used by the logical replication is well defined [in the official PostgreSQL documentation](https://www.postgresql.org/docs/10/protocol-logicalrep-message-formats.html). It's a binary protocol, so we're going to use [the `scodec` library]() to decode what's emitted by `pg_recvlogical`. 

First we need to define all the classes representing the binary messages:

{{< highlight sql >}}

sealed trait Message

object Message {

    case class Begin(lsn: LogSequenceNumber, commitInstant: Instant, xid: TransactionID) extends Message

    case class Commit(commitLSN: LogSequenceNumber, transactionEndLSN: LogSequenceNumber, commitTimestamp: Instant) extends Message

    case class Insert(relationID: RelationID, tupleData: TupleData) extends Message

    case class Update(relationID: RelationID, submessage: Submessage, newTupleData: TupleData) extends Message

    case class Delete(relationId: RelationID, submessage: Submessage) extends Message

    case class Relation(id: RelationID, namespace: RelationNamespace, name: RelationName, replicaIdentitySetting: Int, columns: List[Column]) extends Message

}

{{< / highlight >}}

Let's ignore the `RelationID`, `LogSequenceNumber` types (as they actually are aliases for `Long` or `Int`) and focus on the `TupleData` one:

{{< highlight sql >}}

[//]: # <<< tuple-data-class-definition >>>

sealed trait Value

object Value {

    case object Null extends Value

    case class Text(value: ByteVector) extends Value

    case object Toasted extends Value

}

{{< / highlight >}}

It's in the `Value` type where the value lies: 
* The `Value.Null` case object represents SQL's `NULL` value; 
* `Value.Toasted` is for [large columns](https://blog.gojekengineering.com/a-toast-from-postgresql-83b83d0d0683); 
* Finally, `Value.Text` contains actual values (in bytes: the protocol does not express value types).

#### Codec implementation

Our model classes are ready, so now we need to find a way to parse the binary messages coming from `pg_receival` to an actual `Message` instance. Using `scodec`, we do that by defining a `Codec`s this way:

{{< highlight scala >}}

[//]: # <<< message-codec >>>

{{< / highlight >}}

You see that for each kind of message, we have the corresponding `Codec`: for example, the `insert` variable is a `Codec[Message.Insert]` defined like this:

{{< highlight scala >}}

val insert: Codec[Message.Insert] = {
    ("relationId" | int32) ::
    constant('N') ::
    ("newTupleData" | tupleData)
}.as[Message.Insert]

{{< / highlight >}}


### Mapping using the `TupleDataReader` class and the `shapeless` library

#### Quick break

Okay! Let's do a quick summary of what we've done so far:
* We configured PostgreSQL to let it emit changes through its Logical Replication capability;
* We receive the changes by directly invoking the `pg_recvlogical` binary and capturing its binary output;
* We decoded the binary output to `Message` instances by using the appropriate `scodec`'s `Codec` instance;
* In the `Message.Insert` or `Message.Update` classes, we have access to the actual values of the row through the `TupleData` type which is actually a `List` of `Value`s;
* Among others, a `Value` may be a `Value.Text` containing bytes which represent the actual value stored in PostgreSQL.  

So what's remaining now is: how to convert a `Message.Inserted` instance to a custom case class that will the inserted row?

#### The `ValueReader` typeclass

First, we're going to define a `ValueReader[T]` typeclass which will have the role of trying to convert a `Value` to a `T`. 

Technically speaking, it's a `trait` with a single `read` method that we'll have to call to do the conversion: 

{{< highlight scala >}}

[//]: # <<< value-reader-trait >>>

{{< / highlight >}}

As we said that `ValueReader[T]` is a typeclass, it requires some instances to work with. For our little project here, we're only going to be able to read `String`, `Long` and `Int`. 

Creating a `ValueReader[String]` is quite straighforward: 

{{< highlight scala >}}

[//]: # <<< value-reader-for-string-instance >>>

{{< / highlight >}}

A `ValueReader[Double]` is not that much complicated:

{{< highlight scala >}}

[//]: # <<< value-reader-for-double-instance >>>

{{< / highlight >}}

Using the `ValueReader[T]` typeclass allow us to map a `Value` of a `TupleData` to a `T`, but it doesn't allow us to map an entire `TupleData` to something else.

#### The `TupleDataReader` typeclass

We're going to use the exact same strategy as above: we define a `TupleDataReader[T]` with a single `read` method which will take a `TupleData` instance and return an instance of `T`.  

{{< highlight scala >}}

[//]: # <<< tuple-data-reader-trait >>>

{{< / highlight >}}

Again, as it's a typeclass, it require some instances. 

So for example, if we have a `Person` case class defined like this:

{{< highlight scala >}}

[//]: # <<< person-class >>>

{{< / highlight >}}

We can have a `TupleDataReader[Person]` instance like this:

{{< highlight scala >}}

[//]: # <<< tuple-data-reader-for-person-instance >>>

{{< / highlight >}}

But... Think about it: it's a shame that, for all the case classes, we have to define a `TupleDataReader` instance! If we know in advance the case class, is there a way to derive this instance automatically?


#### The `shapeless` library to the rescue! 

I think you already heard about the `shapeless` library. It allow a lot of stuff, but we'll focus on the HNil one. 

Thanks to Shapeless, we can express a complex type as a heterogeneous list of types. So, _in terms of types_, the following `HPerson` type and `Person` case class are semanticaly equivalent:

{{< highlight scala >}}

type HPerson = String :: String :: HNil

case class Person(firstName: String, lastName: String)

{{< / highlight >}}

The real power of `shapeless` is that it provide a bijection between the case classes world and the hetereogeneous list of types world by using the `Generic[T]` and its `from` and `to` method. 

You may ask: "What is the point of having an heterogeneous list of types when you have case classes?" And that's a good question! 

The fact of working with lists allow you to take advantage of one of its intrinsec property: recursivity. 



### Embedding everything under an `fs2`'s `Pipe`s

#### Overview

Now that we have all the classes that we have, we can embbed everything into a `Stream[F[_], Change[T]]` in order to build our Debezium-like library.

We're going to do that in 3 steps:
* First, a `Stream[F[_], Byte]` which will invoke `...` and retreive the output
* Then, a `Pipe[F[_], Byte, Message]` which will decode the `Byte` stream to an actual `Message` stream
* And finally, a `Pipe[F[_], Message, Change[T]]` which will convert the `Message` stream to `Change[T]` using the `TupleDataReader` defined above. 


#### Retreiving the `pg_recvlogical`'s output

We first have to define a small `CaptureConfig` case class which will store all the config value required to connect to the running PostgreSQL instance (`user`, `host`, `port`, etc.). 

{{< highlight scala >}}

case class CaptureConfig(
  user: String,
  password: String,
  database: String,
  host: String,
  port: Int,
  slot: String,
  publications: List[String]
)

{{< / highlight >}}

And now, using the the `fs2-io` extension, it's actually quite easy to invoke the `pg_receiva` process and capture its output.

{{< highlight scala >}}

def receive[F[_]: Sync: ContextShift: Concurrent](config: CaptureConfig): Stream[F, Byte] = {
  (for {
    blocker <- Stream.resource[F, Blocker](Blocker[F])
    process <- Stream.bracket[F, Process](F.delay({
      new ProcessBuilder()
        .command("pg_recvlogical",
          "-d", s"${config.database}",
          "-U", s"${config.user}",
          "-h", s"${config.host}",
          "-p", s"${config.port}",
          s"--slot=${config.slot}",
          "--status-interval=1",
          "--fsync-interval=1",
          "--file=-",
          "--no-loop",
          "-v",
          "--option=proto_version=1",
          s"--option=publication_names=${config.publications.mkString(",")}",
          "--plugin=pgoutput",
          "--create",
          "--if-not-exists",
          "--start")
        .start()
    }))({ process =>
      F.delay(process.destroy())
    })
  } yield (blocker, process)).flatMap({ case (blocker, process) =>
    readInputStream(F.delay(process.getInputStream), 512, blocker)
      .concurrently(readInputStream(F.delay(process.getErrorStream), 512, blocker).through(text.utf8Decode).showLines(System.err))
  })
}

{{< / highlight >}}

Not a lot of things to say, it's actually quite straighforward. 


#### From `Stream[F, Byte]`s to `Stream[F, Message]`

Okay, this one is a little bit tricky. Until now, we concidered each messages one by one. But `pg_recvlogical` is all the changes and the protocol does not define a single message size, and there is no separator to help us split a `Byte` stream to `Message` instances. 

To bypass that, we're going to try to read `Byte` chunks, and it may either:
* Fail because there is not enough bytes, and in that case we're just going to read more bytes and try again;
* Succeed, which means retreiving both:
  * An instance of `Message` that we're going to emit,
  * Some remaning bytes that'we going to reuse in the next iteration.

In order to do that, we're going to switch from the push mode of `fs2` to the pull one and implement this algorithm using `Pull[F, Message, Unit]` and go back to `Stream[F, Message]` in the end. 

{{< highlight scala >}}

[//]: # <<< messages-method >>>

{{< / highlight >}}


#### From `Stream[F, Message]` to `Stream[F, Change[T]]`

This one is actually easy: we're just going to keep the `Message.Insert`, `Message.Update` and `Message.Delete` extracting the relevent `TupleData` and read it as `T` the `TupleDataReader`

But first, as we still need to distinguish inserts, updates or deletes, let's define the `Change[T]` sealed trait to hold the actual `T` instance:

{{< highlight scala >}}

[//]: # <<< change-trait >>>

{{< / highlight >}}

Now, we can define the `Change.changes` method that which will rely on the `TupleDataReader`:

{{< highlight scala >}}

[//]: # <<< changes-method >>>

{{< / highlight >}}


#### Assemble everything

Thanks to `fs2`'s expressiveness, it's actually straighforward:

{{< highlight scala >}}

[//]: # <<< capture-method >>>

{{< / highlight >}}

And that's it, we can now use our library with `Change.capture[`
