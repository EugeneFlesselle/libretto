package libretto

import scala.concurrent.duration._

class StreamsTests extends TestSuite {
  import kit.dsl._
  import kit.coreLib._
  import kit.scalaLib._
  
  val scalaStreams = ScalaStreams(kit.dsl, kit.coreLib, kit.scalaLib, kit.coreStreams)
  
  import scalaStreams._
  
  test("toList ⚬ fromList = id") {
    assertVal(
      Pollable.fromList(List(1, 2, 3, 4, 5, 6)) >>> Pollable.toList,
      List(1, 2, 3, 4, 5, 6),
    )
  }

  test("Pollable.map") {
    assertVal(
      Pollable.fromList(List(1, 2, 3)) >>> Pollable.map(_.toString) >>> Pollable.toList,
      List("1", "2", "3"),
    )
  }

  test("partition") {
    assertVal(
      Pollable.of(1, 2, 3, 4, 5, 6)
        .>>>(Pollable.map { i => if (i % 2 == 0) Left(i) else Right(i) })
        .>>>(Pollable.partition)
        .par(Pollable.toList, Pollable.toList)
        .>>>(unliftPair),
      (List(2, 4, 6), List(1, 3, 5)),
    )
  }

  test("concat") {
    assertVal(
      parFromOne(Pollable.of(1, 2, 3), Pollable.of(4, 5, 6))
        .>(Pollable.concat)
        .>(Pollable.toList),
      List(1, 2, 3 ,4, 5, 6),
    )
  }
}
