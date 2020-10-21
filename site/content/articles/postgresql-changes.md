---
title: How to make a quick'n'dirty CDC with PostgreSQL
date: 2020-10-16
---

# How to make a quick'n'dirty CDC with PostgreSQL

## Overview
At `${WORK}`, I encountered multiple times some technical architectures that rely on quite powerful tools that allow us to retreive all the changes that happened to a database: it's called a Change Data Capture. 

Among multiple usage, here are the three that I encountered : 
* *Data Offload*: 
* *Outbox Pattern*:

Multiple tools exists: 
* Debezium (de facto)


## What are we going to do?
In order to have a better understanding how everything works together, we're going to build a small Debezium-like library which will listen to PostgreSQL changes in Scala, using `fs2` and `scodec` in a `cats`-friendly way. 


## But wait... How does it work?

### A little bit of theory, first...
#### In general



#### Postgres specific
Since v10, Postgres allow us to...


## Let's play with PostgreSQL
### Start a new Database Cluster
Okay, let's get started! First, let's write 3 small `make` targets to run a PostgreSQL instance locally: 

{{< highlight make >}}
PGDATA := postgres

## Initialize a PostgreSQL database cluster
$(PGDATA)/PG_VERSION:
	initdb \
		-D "$(PGDATA)" \
		-A "trust" \
		-U "postgres"

## Start PostgreSQL database server
.PHONY: pg-start
pg-start: $(PGDATA)/PG_VERSION
	postgres \
		-D "$(PGDATA)" \
		-c unix_socket_directories="$(PWD)" \
		-c wal_level="logical" \
		-c max_replication_slots=1 

## Execute a SQL file
.PHONY: pg-run
pg-run:
	psql \
		-U "postgres" \
		-h "localhost" \
		-p 5432 \
		-f "$(SQL_FILE)"
{{< / highlight >}}

A few words here: 
* In order to be able to use PostgreSQL's Logical Replication, we have to setup the `wal_level` to `logical` in order to let the database export and in our case here;
* We're setting the `max_replication_slots` to `1` as there will be only one slot that is going to be used. 

Using this, we can now start PostgreSQL by running `make pg-start` and executing a SQL file by running `make pg-run SQL_FILE=<...>` which is quite convenient.

### Create a replicated table
We're going to create a `persons` and listen to its change. Quite easy: 

{{< highlight sql >}}
CREATE TABLE
    persons (
        id SERIAL PRIMARY KEY,
        firstName TEXT, 
        lastName TEXT, 
        tags TEXT ARRAY
    );

ALTER TABLE 
    persons 
REPLICA IDENTITY 
    FULL;
{{< / highlight >}}

See the `REPLICA IDENTITY` property? It control the [behavior of the Logical Replication](https://www.postgresql.org/docs/13/sql-altertable.html#SQL-CREATETABLE-REPLICA-IDENTITY) of the table and may be set with the following values:
* `DEFAULT` (which is obviously the default value...): on an `UPDATE`, in the _Old_ part of the change message, PostgreSQL will emit only the values composing the _Primary Key_; 
* `USING INDEX <index_name>`: it's the same as above, except that it take the values listed in the `index_name` index definition; 
* `FULL` (that we're using): the message will be compose by both the _old_ and the _new_ values of all the columns. 

### Insert some rows
We're going to use a sample files (named `persons.txt`... Original, right?) to fill our table using this small loop: 

{{< highlight bash >}}
while sleep 1; do
		psql <<EOSQL \
			-U "postgres" \
			-h "localhost" \
			-p 5432 \
			-v last_name="'$$( shuf -n1 "persons.txt" | cut -d":" -f1 )'" \
			-v first_name="'$$( shuf -n1 "persons.txt" | cut -d":" -f2  )'" \
	INSERT INTO persons(firstName, lastName) VALUES (:last_name, :first_name)
EOSQL
done
{{< / highlight >}}

And let's wrap this into another small `make` target:
{{< highlight make >}}
.PHONY: pg-populate
pg-populate: pg-

{{< / highlight >}}

### Listen for changes
Okay! Now we have a running PostgreSQL instance and a fake workload which produce data. We can now listen for the changes! 

Great! We see that changes are publicated and we have a way to receive them... But how to use it in our program?

## Deserializing 
### The `CaptureSpec` abstract class
In order to 

### Protocol decoding with the `scodec` library

The protocol used by the logical replication is well defined [here](). We're going to use `scodec` in order to deserialize it and use it programmaticaly. First, we need to define all the classes in which we're going to map everything. 

Basically, there is 5 kinds of messages that can be decoded that we're going to model with a `sealed trait Message`: 
* `Begin()`
* `Commit()`
* `Insert()`
* `Update()`
* `Delete()`


### Mapping using the `TupleDataReader` class and the `shapeless` library

We're able to obtain all kind of `Message`s that are emitted by PostgreSQL. But what about converting them to actual case classes?

### Embedding everything under an `fs2`'s `Pipe`s

#### Overview

Now that we have all the classes that we have, we can embbed everything into a Stream[F[_], Change[T]] in order to build our Debezium-like library.

We're going to do that in 3 steps:
* First, a Stream[F[_], Byte] which will invoke `...` and retreive the output
* Then, a Pipe[F[_], Byte, Message] which will decode the `Byte` stream to an actual `Message` stream
* And finally, a Pipe[F[_], Message, Change[T]] which will convert the `Message` stream to `Change[T]` using the `TupleDataReader` defined before. 


#### Retreiving the `...`'s output

Using the `fs2-io` extension, it's actually quite easy. We first have to define a small `CaptureConfig` case class which will store all the config value required to connect to the running PostgreSQL instance (`user`, `host`, `port`, etc.). 

{{< highlight scala >}}
def receive[F[_]: Sync: ContextShift](config: CaptureConfig): Stream[F, Byte] = {
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
            "--file=-",
            "--no-loop",
            "--option=proto_version=1",
            s"--option=publication_names=${config.publications.mkString(",")}",
            "--plugin=pgoutput",
            "--start")
          .start()
      }))({ process =>
        F.delay(process.destroy())
      })
    } yield (blocker, process)).flatMap({ case (blocker, process) =>
      readInputStream(F.delay(process.getInputStream), 512, blocker)
    })
  }
{{< / highlight >}}

Not a lot of things to say, it's actually quite straighforward. 


#### Toto

Okay, this one is a little bit tricky. Until now, we concidered each messages one by one. 

The strategy here is quite easy. We're going to try to read `Byte` chunks, and it may either:
* Fail because there is not enough bytes, and in that case we're just going to read more bytes and try again
* Succeed, which means retreiving both an instance of `Message` and remaning bytes (that'we going to reuse in the next iteration)

In order to do that, we're going to switch from the push mode of `fs2` to the pull one, using the `pull` method which will make the things more readable. 

{{< highlight scala >}}
def messages[F[_]: RaiseThrowable]: Pipe[F, Byte, Message] = { stream =>
  def go(leftStream: Stream[F, BitVector], leftBits: BitVector, first: Boolean): Pull[F, Message, Unit] = {
    def moveOn() = leftStream.pull.uncons1.flatMap({
      case Some((rightBits, rightStream)) =>
        go(rightStream, leftBits ++ rightBits, first)

      case None =>
        Pull.done
    })

    val bitsToDecode = leftBits.toByteVector.drop(if (first) 0 else 1).toBitVector
    messageCodec.decode(bitsToDecode) match {
      case Attempt.Successful(DecodeResult(message, remainingBits)) =>
        Pull.output1[F, Message](message) *> go(leftStream, remainingBits, false)

      case Attempt.Failure(InsufficientBits(_, _, _)) =>
        moveOn()

      case Attempt.Failure(General(message, _)) if GeneralMessagesToIgnore.exists(message.contains(_)) =>
        moveOn()

      case Attempt.Failure(cause) =>
        Pull.raiseError[F](new Exception(s"${cause}"))
    }
  }

  go(stream.chunks.map(_.toBitVector), BitVector.empty, true).stream
}
{{< / highlight >}}


#### 

This one is actually easy: we're just going to keep the `Message.Insert`, `Message.Update` and `Message.Delete` extracting the relevent `TupleData` and read it using our `TupleDataReader`

{{< highlight scala >}}
def changes[F[_]: RaiseThrowable, T](implicit tupleDataReaderForT: TupleDataReader[T]): Pipe[F, Message, Change[T]] = { messages =>
  messages
    .flatMap({
      case Message.Insert(_, newTupleData) =>
        tupleDataReaderForT
          .read(newTupleData)
          .map({ newValue =>
            Change.Insert(newValue)
          })
          .fold({ throwable =>
            Stream.raiseError[F](throwable)
          }, { insert =>
            Stream.emit[F, Change[T]](insert)
          })

      case Message.Update(_, Submessage.Old(oldTupleData), newTupleData) =>
        (for {
          oldValue <- tupleDataReaderForT.read(oldTupleData)
          newValue <- tupleDataReaderForT.read(newTupleData)
        } yield Change.Update(oldValue, newValue))
          .fold({ throwable =>
            Stream.raiseError[F](throwable)
          }, { update =>
            Stream.emit[F, Change[T]](update)
          })

      case Message.Update(_, _, _) =>
        Stream.raiseError[F](new Exception("You should configure your table with REPLICAS FULL"))

      case _ =>
        Stream.empty[F]
    })
}
{{< / highlight >}}

