/*
 * Copyright (c) 2020 the fs2-kinesis-firehose contributors.
 * See the project homepage at: https://zakolenko.github.io/fs2-kinesis-firehose
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fs2.aws.kinesis.firehose

import cats.effect.{Resource, Sync}
import cats.implicits._
import com.amazonaws.services.kinesisfirehose.model._
import org.junit.Test
import fs2.aws.kinesis.firehose.JavaConversions._

import scala.util.Random

class FirehoseTest extends BaseFirehoseTest {
  import FirehoseTest._

  @Test
  def put(): Unit = {
    runSync {
      for {
        firehose <- firehoseR
        stream <- firehose.testStream()
        _ <- Resource.liftF(firehose.put(
          new PutRecordRequest()
            .withDeliveryStreamName(stream)
            .withRecord(randomRecord)
        ))
      } yield ()
    }
  }

  @Test
  def batchPut(): Unit = {
    runSync {
      for {
        firehose <- firehoseR
        stream <- firehose.testStream()
        response <- Resource.liftF(
          firehose.put(new PutRecordBatchRequest()
            .withDeliveryStreamName(stream)
            .withRecords(randomRecord, randomRecord)
          )
        )
      } yield assert(response.getFailedPutCount == 0)
    }
  }

  @Test
  def describeNonExistentStream(): Unit = {
    runSync {
      for {
        firehose <- firehoseR
        res <- Resource.liftF(firehose.describeStream(
          new DescribeDeliveryStreamRequest().withDeliveryStreamName(Random.alphanumeric.take(10).mkString)
        ))
      } yield assert(res.isEmpty)
    }
  }

  @Test
  def describeExistingStream(): Unit = {
    runSync {
      for {
        firehose <- firehoseR
        stream <- firehose.testStream()
        res <- Resource.liftF(firehose.describeStream(
          new DescribeDeliveryStreamRequest().withDeliveryStreamName(stream)
        ))
      } yield assert(res.nonEmpty)
    }
  }

  @Test
  def listSteams(): Unit = {
    runSync {
      for {
        firehose <- firehoseR
        stream <- firehose.testStream()
        res <- Resource.liftF(firehose.listStreams(new ListDeliveryStreamsRequest()))
      } yield assert(res.getDeliveryStreamNames.asScala == List(stream))
    }
  }
}

object FirehoseTest {
  implicit class FirehoseOps[F[_]](private val f: Firehose[F]) extends AnyVal {
    import fs2.aws.kinesis.firehose.implicits._

    def testStream(name: String = s"test-stream-${Random.alphanumeric.take(5).mkString}")(implicit F: Sync[F]): Resource[F, String] = {
      f.streamAsResource(
        new CreateDeliveryStreamRequest()
          .withDeliveryStreamName(name)
          .withDeliveryStreamType(DeliveryStreamType.DirectPut)
      ).as(name)
    }
  }
}