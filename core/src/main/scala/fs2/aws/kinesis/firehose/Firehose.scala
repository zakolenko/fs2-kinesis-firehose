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

import cats.effect.{Ref, Resource, Sync}
import cats.implicits._
import com.amazonaws.services.kinesisfirehose.model._
import com.amazonaws.services.kinesisfirehose._
import retry.{RetryPolicy, Sleep}

import scala.jdk.CollectionConverters.{CollectionHasAsScala, IterableHasAsJava}

trait Firehose[F[_]] {

  def put(record: PutRecordRequest): F[PutRecordResult]

  def put(records: PutRecordBatchRequest): F[PutRecordBatchResult]

  def putWithRetry(request: PutRecordBatchRequest, retryPolicy: RetryPolicy[F]): F[PutRecordBatchResult]

  def createStream(request: CreateDeliveryStreamRequest): F[CreateDeliveryStreamResult]

  def deleteStream(request: DeleteDeliveryStreamRequest): F[Option[DeleteDeliveryStreamResult]]

  def describeStream(request: DescribeDeliveryStreamRequest): F[Option[DescribeDeliveryStreamResult]]

  def listStreams(request: ListDeliveryStreamsRequest): F[ListDeliveryStreamsResult]

  def listTags(request: ListTagsForDeliveryStreamRequest): F[Option[ListTagsForDeliveryStreamResult]]

  def tag(request: TagDeliveryStreamRequest): F[Option[TagDeliveryStreamResult]]

  def untag(request: UntagDeliveryStreamRequest): F[Option[UntagDeliveryStreamResult]]

  def startStreamEncryption(
    request: StartDeliveryStreamEncryptionRequest
  ): F[Option[StartDeliveryStreamEncryptionResult]]

  def stopStreamEncryption(request: StopDeliveryStreamEncryptionRequest): F[Option[StopDeliveryStreamEncryptionResult]]

  def updateDest(request: UpdateDestinationRequest): F[Option[UpdateDestinationResult]]
}

object Firehose {

  def apply[F[_]: Sync: Sleep](client: AmazonKinesisFirehose): Firehose[F] =
    new Firehose[F] {
      import ErrorUtils._

      override def put(record: PutRecordRequest): F[PutRecordResult] = Sync[F].blocking(client.putRecord(record))

      override def put(records: PutRecordBatchRequest): F[PutRecordBatchResult] =
        Sync[F].blocking(client.putRecordBatch(records))

      override def putWithRetry(
        request: PutRecordBatchRequest,
        retryPolicy: RetryPolicy[F]
      ): F[PutRecordBatchResult] = {
        import retry._

        for {
          reqRef <- Ref.of[F, PutRecordBatchRequest](request)
          res <- {
            retryingOnFailures[Either[Throwable, PutRecordBatchResult]](
              retryPolicy,
              _.fold(_ => false, _.getFailedPutCount <= 0).pure[F],
              (errorOrResp, _) => {
                errorOrResp.fold(
                  _ => Sync[F].unit,
                  resp => {
                    reqRef.update { req =>
                      val newReq = req.clone()

                      newReq.withRecords(
                        newReq.getRecords.asScala.iterator
                          .zip(resp.getRequestResponses.asScala.iterator)
                          .filter { case (_, rec) => rec.getRecordId eq null }
                          .map(_._1)
                          .toList
                          .asJavaCollection
                      )
                    }
                  }
                )
              }
            )(reqRef.get.flatMap(put(_).redeem(Left(_), Right(_))))
          }
          r <- res.liftTo[F]
        } yield r
      }

      override def createStream(request: CreateDeliveryStreamRequest): F[CreateDeliveryStreamResult] =
        Sync[F].blocking(client.createDeliveryStream(request))

      override def deleteStream(request: DeleteDeliveryStreamRequest): F[Option[DeleteDeliveryStreamResult]] =
        Sync[F].blocking(client.deleteDeliveryStream(request)).handle404()

      override def describeStream(request: DescribeDeliveryStreamRequest): F[Option[DescribeDeliveryStreamResult]] =
        Sync[F].blocking(client.describeDeliveryStream(request)).handle404()

      override def listStreams(request: ListDeliveryStreamsRequest): F[ListDeliveryStreamsResult] =
        Sync[F].blocking(client.listDeliveryStreams(request))

      override def listTags(request: ListTagsForDeliveryStreamRequest): F[Option[ListTagsForDeliveryStreamResult]] =
        Sync[F].blocking(client.listTagsForDeliveryStream(request)).handle404()

      override def tag(request: TagDeliveryStreamRequest): F[Option[TagDeliveryStreamResult]] =
        Sync[F].blocking(client.tagDeliveryStream(request)).handle404()

      override def untag(request: UntagDeliveryStreamRequest): F[Option[UntagDeliveryStreamResult]] =
        Sync[F].blocking(client.untagDeliveryStream(request)).handle404()

      override def startStreamEncryption(
        request: StartDeliveryStreamEncryptionRequest
      ): F[Option[StartDeliveryStreamEncryptionResult]] =
        Sync[F].blocking(client.startDeliveryStreamEncryption(request)).handle404()

      override def stopStreamEncryption(
        request: StopDeliveryStreamEncryptionRequest
      ): F[Option[StopDeliveryStreamEncryptionResult]] =
        Sync[F].blocking(client.stopDeliveryStreamEncryption(request)).handle404()

      override def updateDest(request: UpdateDestinationRequest): F[Option[UpdateDestinationResult]] = {
        Sync[F].blocking(client.updateDestination(request)).handle404()
      }
    }

  def apply[F[_]: Sync: Sleep](
    builder: AmazonKinesisFirehoseClientBuilder
  ): Resource[F, Firehose[F]] = {
    Resource.make(Sync[F].blocking(builder.build()))(k => Sync[F].blocking(k.shutdown())).map(Firehose(_))
  }

  def default[F[_]: Sync: Sleep]: Resource[F, Firehose[F]] = {
    Firehose(AmazonKinesisFirehoseClient.builder())
  }
}
