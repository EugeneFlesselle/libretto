package libretto.testing

import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService}
import libretto.{CoreLib, Monad, ScalaBridge, ScalaExecutor, ScalaDSL, StarterKit}
import libretto.util.{Monad => ScalaMonad}
import libretto.util.Monad.syntax._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import libretto.testing.ScalaTestExecutor.ExecutionParam.Instantiation

object StarterTestExecutor {

  private class StarterTestKitFromBridge[Bridge <: ScalaBridge.Of[StarterKit.dsl.type]](
    bridge: Bridge,
  )(using
    ScalaMonad[Future],
  ) extends ScalaTestExecutor.ScalaTestKitFromBridge[StarterKit.dsl.type, Bridge](StarterKit.dsl, bridge)
       with StarterTestKit

  def fromExecutor(
    exec: ScalaExecutor.OfDsl[StarterKit.dsl.type],
  )(using
    ScalaMonad[Future],
  ): TestExecutor[StarterTestKit] =
    new TestExecutor[StarterTestKit] {
      override val name: String =
        StarterTestExecutor.getClass.getCanonicalName

      override val testKit: StarterTestKitFromBridge[exec.bridge.type] =
        new StarterTestKitFromBridge(exec.bridge)

      import testKit.{ExecutionParam, Outcome}
      import testKit.dsl._
      import testKit.probes.Execution

      override def runTestCase[O, P, Y](
        body: Done -⚬ O,
        params: ExecutionParam[P],
        conduct: (exn: Execution) ?=> (exn.OutPort[O], P) => Outcome[Y],
        postStop: Y => Outcome[Unit],
      ): TestResult[Unit] = {
        val p: Instantiation[P, exec.ExecutionParam] =
          ScalaTestExecutor.ExecutionParam.instantiate(params)(using exec.ExecutionParam)

        TestExecutor
          .usingExecutor(exec)
          .runTestCase[O, p.X, Y](
            body,
            p.px,
            (port, x) => Outcome.toAsyncTestResult(conduct(port, p.get(x))),
            postStop andThen Outcome.toAsyncTestResult,
          )
      }

      override def runTestCase(body: () => Outcome[Unit]): TestResult[Unit] =
        TestExecutor
          .usingExecutor(exec)
          .runTestCase(() => Outcome.toAsyncTestResult(body()))
    }

  def fromJavaExecutors(
    scheduler: ScheduledExecutorService,
    blockingExecutor: ExecutorService,
  ): TestExecutor[StarterTestKit] = {
    val executor0: libretto.ScalaExecutor.OfDsl[StarterKit.dsl.type] =
      StarterKit.executor(blockingExecutor)(scheduler)

    given monadFuture: ScalaMonad[Future] =
      ScalaMonad.monadFuture(using ExecutionContext.fromExecutor(scheduler))

    fromExecutor(executor0)
  }

  lazy val global: TestExecutor[StarterTestKit] =
    fromJavaExecutors(
      scheduler        = Executors.newScheduledThreadPool(4),
      blockingExecutor = Executors.newCachedThreadPool(),
    )
}
