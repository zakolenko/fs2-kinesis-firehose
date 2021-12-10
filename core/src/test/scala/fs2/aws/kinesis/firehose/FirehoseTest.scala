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

import java.nio.ByteBuffer
import java.util.concurrent.Executors

import cats.effect.{Blocker, IO, Resource, Sync}
import cats.implicits._
import com.amazonaws.services.kinesisfirehose.AmazonKinesisFirehoseClientBuilder
import com.amazonaws.services.kinesisfirehose.model._
import com.dimafeng.testcontainers.LocalStackContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import fs2.aws.kinesis.firehose.JavaConversions._
import fs2.aws.kinesis.firehose.implicits._
import munit.CatsEffectSuite
import org.testcontainers.containers.localstack.LocalStackContainer.Service

import scala.concurrent.ExecutionContext
import scala.util.Random

class FirehoseTest extends CatsEffectSuite with TestContainerForAll {
  import FirehoseTest._

  override val containerDef = LocalStackContainer.Def(services = List(Service.FIREHOSE))

  val firehose = ResourceSuiteLocalFixture(
    "firehose",
    Resource
      .make(IO(Executors.newSingleThreadExecutor()))(ex => IO(ex.shutdown()))
      .map(ExecutionContext.fromExecutor)
      .map(Blocker.liftExecutionContext)
      .flatMap { blocker =>
        withContainers { firehose =>
          Firehose[IO](
            AmazonKinesisFirehoseClientBuilder
              .standard()
              .withEndpointConfiguration(firehose.container.getEndpointConfiguration(Service.FIREHOSE))
              .withCredentials(firehose.container.getDefaultCredentialsProvider),
            blocker
          )
        }
      }
  )

  def firehoseR: Resource[IO, Firehose[IO]] = Resource.eval[IO, Firehose[IO]](IO(firehose()))

  test("put") {
    for {
      firehose <- firehoseR
      stream <- firehose.testStream()
      _ <- Resource.eval(
        firehose.put(
          new PutRecordRequest().withDeliveryStreamName(stream).withRecord(randomRecord)
        )
      )
    } yield ()
  }

  test("batchPut") {
    for {
      firehose <- firehoseR
      stream <- firehose.testStream()
      response <- Resource.eval(firehose.batchPut(stream, List.fill(500)(randomBytes(1000))))
    } yield assert(response.getFailedPutCount == 0)
  }

  test("describeNonExistentStream") {
    for {
      firehose <- firehoseR
      res <- Resource.eval(
        firehose.describeStream(
          new DescribeDeliveryStreamRequest().withDeliveryStreamName(Random.alphanumeric.take(10).mkString)
        )
      )
    } yield assert(res.isEmpty)
  }

  test("describeExistingStream") {
    for {
      firehose <- firehoseR
      stream <- firehose.testStream()
      res <- Resource.eval(
        firehose.describeStream(
          new DescribeDeliveryStreamRequest().withDeliveryStreamName(stream)
        )
      )
    } yield assert(res.nonEmpty)
  }

  test("listSteams") {
    for {
      firehose <- firehoseR
      stream <- firehose.testStream()
      res <- Resource.eval(firehose.listStreams(new ListDeliveryStreamsRequest()))
    } yield assert(res.getDeliveryStreamNames.asScala == List(stream))
  }

  protected def randomBytes(size: Int = 20): Array[Byte] = {
    val bytes = new Array[Byte](size)
    scala.util.Random.nextBytes(bytes)
    bytes
  }

  protected def randomRecord: Record = new Record().withData(ByteBuffer.wrap(randomBytes()))
}

object FirehoseTest {

  implicit class FirehoseOps[F[_]](private val f: Firehose[F]) extends AnyVal {
    import fs2.aws.kinesis.firehose.implicits._

    def testStream(
      name: String = s"test-stream-${Random.alphanumeric.take(5).mkString}"
    )(implicit F: Sync[F]): Resource[F, String] = {
      f.streamAsResource(
          new CreateDeliveryStreamRequest()
            .withDeliveryStreamName(name)
            .withDeliveryStreamType(DeliveryStreamType.DirectPut)
        )
        .as(name)
    }
  }
}
