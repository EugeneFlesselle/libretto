package libretto

class StreamsTests extends TestSuite {
  import kit.dsl._
  import kit.coreLib._
  import kit.scalaLib._
  
  val streams = CoreStreams(kit.dsl, kit.coreLib)
  val scalaStreams = ScalaStreams(kit.dsl, kit.coreLib, kit.scalaLib, streams)
  
  import scalaStreams._
  
  testResult("toList ⚬ fromList = id") {
    const_(List(1, 2, 3, 4, 5, 6)) >>> Pollable.fromList >>> Pollable.toList
  } (
    List(1, 2, 3, 4, 5, 6)
  )
}
