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

import cats.Applicative
import retry.RetryPolicy

import scala.concurrent.duration._

trait ProducerSettings[F[_]] {
  def deliveryStream: String
  def withDeliveryStream(deliveryStream: String): ProducerSettings[F]

  def separator: Array[Byte]
  def withSeparator[S: Serializer](s: S): ProducerSettings[F]

  def batchSize: Int
  def withBatchSize(batchSize: Int): ProducerSettings[F]

  def parallelism: Int
  def withParallelism(parallelism: Int): ProducerSettings[F]

  def timeWindow: FiniteDuration
  def withTimeWindow(timeWindow: FiniteDuration): ProducerSettings[F]

  def retryPolicy: Option[RetryPolicy[F]]
  def withRetryPolicy(retryPolicy: RetryPolicy[F]): ProducerSettings[F]
}

object ProducerSettings {

  private[this] case class ProducerSettingsImpl[F[_]](
    deliveryStream: String,
    separator: Array[Byte],
    batchSize: Int,
    parallelism: Int,
    timeWindow: FiniteDuration,
    retryPolicy: Option[RetryPolicy[F]]
  ) extends ProducerSettings[F] {
    override def withDeliveryStream(deliveryStream: String): ProducerSettings[F] = copy(deliveryStream = deliveryStream)

    override def withSeparator[S: Serializer](separator: S): ProducerSettings[F] =
      copy(separator = Serializer[S].apply(separator))

    override def withBatchSize(batchSize: Int): ProducerSettings[F] =
      copy(batchSize = batchSize)
    override def withParallelism(parallelism: Int): ProducerSettings[F] = copy(parallelism = parallelism)
    override def withTimeWindow(timeWindow: FiniteDuration): ProducerSettings[F] = copy(timeWindow = timeWindow)

    override def withRetryPolicy(retryPolicy: RetryPolicy[F]): ProducerSettings[F] =
      copy(retryPolicy = Some(retryPolicy))
  }

  def apply[F[_]: Applicative, S: Serializer](deliveryStream: String, separator: S): ProducerSettings[F] = {
    import cats.implicits._
    import retry.RetryPolicies._

    new ProducerSettingsImpl[F](
      deliveryStream = deliveryStream,
      separator = Serializer[S].apply(separator),
      batchSize = 500,
      parallelism = 1,
      timeWindow = 5.seconds,
      retryPolicy = Some(exponentialBackoff[F](500.milliseconds) |+| limitRetries(6))
    )
  }
}
