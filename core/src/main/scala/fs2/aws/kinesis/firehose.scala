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

package fs2.aws.kinesis

import java.nio.ByteBuffer

import cats.Foldable
import cats.effect.{Concurrent, Timer}
import cats.implicits._
import com.amazonaws.services.kinesisfirehose.model.{PutRecordBatchRequest, PutRecordBatchResult, Record}
import retry.RetryPolicy

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

package object firehose {

  private def produce[F[_]: Concurrent](client: Firehose[F])(
    parallelism: Int,
    maybeRetryPolicy: Option[RetryPolicy[F]]
  ): fs2.Pipe[F, PutRecordBatchRequest, PutRecordBatchResult] = {
    val put: PutRecordBatchRequest => F[PutRecordBatchResult] = maybeRetryPolicy match {
      case Some(rp) => client.putWithRetry(_, rp)
      case None     => client.put
    }

    _.mapAsync(parallelism)(put)
  }

  def produceChunks[
    F[_]: Concurrent: Timer,
    C[_]: Foldable,
    T: Serializer
  ](
    stream: String,
    parallelism: Int = 1,
    mRetryPolicy: Option[RetryPolicy[F]] = None
  )(
    client: Firehose[F]
  ): fs2.Pipe[F, C[T], PutRecordBatchResult] = { chunks =>
    chunks
      .map { chunk =>
        val records = chunk.foldLeft(ArrayBuffer.empty[Record]) { (buff, x) =>
          buff += new Record().withData(ByteBuffer.wrap(Serializer[T].apply(x)))
        }

        new PutRecordBatchRequest().withDeliveryStreamName(stream).withRecords(records.asJava)
      }
      .through(produce(client)(parallelism, mRetryPolicy))
  }

  def produce[F[_]: Concurrent: Timer, T: Serializer](settings: ProducerSettings[F])(
    client: Firehose[F]
  ): fs2.Pipe[F, T, PutRecordBatchResult] = { elements =>
    elements
      .groupWithin(settings.batchSize, settings.timeWindow)
      .through(
        produceChunks(
          settings.deliveryStream,
          settings.parallelism,
          settings.retryPolicy
        )(client)
      )
  }
}
