/*
 * Copyright 2020-2022 Typelevel
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

package cats.effect
package unsafe

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

@deprecated("Use default runtime with a custom PollingSystem", "3.5.0")
abstract class PollingExecutorScheduler(pollEvery: Int)
    extends ExecutionContextExecutor
    with Scheduler { outer =>

  private[this] val loop = new EventLoopExecutorScheduler(
    pollEvery,
    new PollingSystem {
      type Poller = outer.type
      type PollData = outer.type
      def makePoller(register: (PollData => Unit) => Unit): Poller = outer
      def makePollData(): PollData = outer
      def closePollData(data: PollData): Unit = ()
      def poll(data: Poller, nanos: Long, reportFailure: Throwable => Unit): Boolean =
        if (nanos == -1) data.poll(Duration.Inf) else data.poll(nanos.nanos)
      def interrupt(targetThread: Thread, targetData: PollData): Unit = ()
    }
  )

  final def execute(runnable: Runnable): Unit =
    loop.execute(runnable)

  final def sleep(delay: FiniteDuration, task: Runnable): Runnable =
    loop.sleep(delay, task)

  def reportFailure(t: Throwable): Unit = loop.reportFailure(t)

  def nowMillis() = loop.nowMillis()

  override def nowMicros(): Long = loop.nowMicros()

  def monotonicNanos() = loop.monotonicNanos()

  /**
   * @param timeout
   *   the maximum duration for which to block. ''However'', if `timeout == Inf` and there are
   *   no remaining events to poll for, this method should return `false` immediately. This is
   *   unfortunate but necessary so that this `ExecutionContext` can yield to the Scala Native
   *   global `ExecutionContext` which is currently hard-coded into every test framework,
   *   including JUnit, MUnit, and specs2.
   *
   * @return
   *   whether poll should be called again (i.e., there are more events to be polled)
   */
  protected def poll(timeout: Duration): Boolean

}
