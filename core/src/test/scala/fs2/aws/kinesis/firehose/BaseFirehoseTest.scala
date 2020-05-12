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

import cats.effect.{Blocker, ContextShift, IO, Resource, Timer}
import cloud.localstack.docker.annotation.LocalstackDockerProperties
import cloud.localstack.{Localstack, LocalstackTestRunner, TestUtils}
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.kinesisfirehose.AmazonKinesisFirehoseClientBuilder
import com.amazonaws.services.kinesisfirehose.model.Record
import org.junit.runner.RunWith

import scala.concurrent.ExecutionContext
import scala.util.Random

@RunWith(classOf[LocalstackTestRunner])
@LocalstackDockerProperties(randomizePorts = true, services = Array("firehose"))
class BaseFirehoseTest {
  protected implicit val CS: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  protected implicit val T: Timer[IO] = IO.timer(ExecutionContext.global)

  TestUtils.setEnv("AWS_CBOR_DISABLE", "1")

  protected def runSync[T](f: Resource[IO, T]): T = f.use(IO.pure).unsafeRunSync()

  protected def randomRecord: Record = new Record().withData(ByteBuffer.wrap {
    val bytes = new Array[Byte](20)
    scala.util.Random.nextBytes(bytes)
    bytes
  })

  protected def firehoseR: Resource[IO, Firehose[IO]] = {
    for {
      blocker <- {
        Resource
          .make(IO(Executors.newSingleThreadExecutor()))(ex => IO(ex.shutdown()))
          .map(ExecutionContext.fromExecutor)
          .map(Blocker.liftExecutionContext)
      }
      firehose <- Firehose[IO](
        AmazonKinesisFirehoseClientBuilder
          .standard()
          .withEndpointConfiguration(
            new AwsClientBuilder.EndpointConfiguration(
              Localstack.INSTANCE.getEndpointFirehose,
              Regions.US_EAST_1.getName
            )
          )
          .withCredentials(new AWSStaticCredentialsProvider(TestUtils.TEST_CREDENTIALS)),
        blocker
      )
    } yield firehose
  }
}
