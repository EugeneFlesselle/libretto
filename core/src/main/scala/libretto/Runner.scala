package libretto

trait Runner[DSL <: CoreDSL, F[_]] {
  val dsl: DSL
  
  import dsl._
  
  def run(prg: One -⚬ Done): F[Unit]
}
