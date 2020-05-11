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

import java.nio.charset.Charset

import org.apache.commons.codec.Charsets

trait Serializer[A] {
  def apply(a: A): Array[Byte]
}

object Serializer {
  def apply[A](implicit a: Serializer[A]): Serializer[A] = a

  implicit val id: Serializer[Array[Byte]] = identity(_)

  implicit def string(charset: Charset = Charsets.UTF_8): Serializer[String] = _.getBytes(charset)
}
