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

import com.amazonaws.services.kinesisfirehose.model.Record

class RecordOps[T](private val t: T) extends AnyVal {

  def asRecord()(implicit ser: Serializer[T]): Record = new Record().withData(ByteBuffer.wrap(ser(t)))
}

trait RecordOpsSyntax {

  implicit def toRecordOps[T](t: T): RecordOps[T] = new RecordOps(t)
}
