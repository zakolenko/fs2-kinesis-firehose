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

import cats.Foldable
import cats.effect.{Resource, Sync}
import cats.implicits._
import com.amazonaws.services.kinesisfirehose.model._
import fs2.aws.kinesis.firehose.implicits._
import fs2.aws.kinesis.firehose.JavaConversions._
import fs2.aws.kinesis.firehose.{Serializer => FirehoseSerializer}

import scala.collection.mutable.ArrayBuffer

class FirehoseOps[F[_]](private val f: Firehose[F]) extends AnyVal {

  def streamAsResource(
    request: CreateDeliveryStreamRequest
  )(implicit F: Sync[F]): Resource[F, CreateDeliveryStreamResult] = {
    val create: F[CreateDeliveryStreamResult] = f.createStream(request)
    val delete: CreateDeliveryStreamResult => F[Unit] = _ =>
      f.deleteStream(new DeleteDeliveryStreamRequest().withDeliveryStreamName(request.getDeliveryStreamName)).void

    Resource.make(create)(delete)
  }

  def put[T: FirehoseSerializer](streamName: String, x: T): F[PutRecordResult] = {
    f.put(
      new PutRecordRequest().withDeliveryStreamName(streamName).withRecord(x.asRecord())
    )
  }

  def batchPut[T: FirehoseSerializer, CC[_]: Foldable](streamName: String, xs: CC[T]): F[PutRecordBatchResult] = {
    f.put(
      new PutRecordBatchRequest()
        .withDeliveryStreamName(streamName)
        .withRecords(xs.foldLeft(ArrayBuffer.empty[Record])(_ += _.asRecord()).asJava)
    )
  }
}

trait FirehoseOpsSyntax {

  implicit def toFirehoseOps[F[_]](firehose: Firehose[F]): FirehoseOps[F] = new FirehoseOps(firehose)
}
