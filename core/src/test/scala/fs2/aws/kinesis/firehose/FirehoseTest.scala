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
import cats.effect.{IO, Resource, Sync}
import cats.implicits._
import com.amazonaws.services.kinesisfirehose.AmazonKinesisFirehoseClientBuilder
import com.amazonaws.services.kinesisfirehose.model._
import com.dimafeng.testcontainers.LocalStackContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import fs2.aws.kinesis.firehose.implicits._
import munit.CatsEffectSuite
import munit.catseffect.IOFixture
import org.testcontainers.containers.localstack.LocalStackContainer.Service

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Random

class FirehoseTest extends CatsEffectSuite with TestContainerForAll {
  import FirehoseTest._

  override val containerDef: LocalStackContainer.Def = LocalStackContainer.Def(services = List(Service.FIREHOSE))

  val firehose: IOFixture[Firehose[IO]] = ResourceSuiteLocalFixture(
    "firehose",
    Resource
      .eval(
        IO(
          withContainers { firehose =>
            AmazonKinesisFirehoseClientBuilder
              .standard()
              .withEndpointConfiguration(firehose.container.getEndpointConfiguration(Service.FIREHOSE))
              .withCredentials(firehose.container.getDefaultCredentialsProvider)
          }
        )
      )
      .flatMap(Firehose[IO](_))
  )

  override def munitFixtures = List(firehose)

  test("put") {
    firehose()
      .testStream()
      .use { stream =>
        firehose().put(
          new PutRecordRequest().withDeliveryStreamName(stream).withRecord(randomRecord)
        )
      }
      .map(_.getRecordId.nonEmpty)
      .assert
  }

  test("batch put") {
    firehose()
      .testStream()
      .use { stream =>
        firehose().batchPut(stream, List.fill(500)(randomBytes(1000)))
      }
      .map(_.getFailedPutCount.toInt)
      .assertEquals(0)
  }

  test("describe non existing stream") {
    firehose()
      .describeStream(new DescribeDeliveryStreamRequest().withDeliveryStreamName(Random.alphanumeric.take(10).mkString))
      .map(_.isEmpty)
      .assert
  }

  test("describe existing stream") {
    firehose()
      .testStream()
      .use { stream =>
        firehose().describeStream(
          new DescribeDeliveryStreamRequest().withDeliveryStreamName(stream)
        )
      }
      .map(_.nonEmpty)
      .assert
  }

  test("list streams") {
    firehose().testStream().use { stream =>
      firehose()
        .listStreams(new ListDeliveryStreamsRequest())
        .map(_.getDeliveryStreamNames.asScala.toList)
        .assertEquals(List(stream))
    }
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
