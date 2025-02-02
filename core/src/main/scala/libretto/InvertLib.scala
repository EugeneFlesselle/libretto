package libretto

object InvertLib {
  def apply(
    coreLib: CoreLib[? <: InvertDSL],
  ): InvertLib[coreLib.type] =
    new InvertLib[coreLib.type](coreLib)
}

class InvertLib[
  CoreLib <: libretto.CoreLib[? <: InvertDSL],
](
  val coreLib: CoreLib,
) {
  import coreLib.dsl._
  import coreLib._

  def inversionDuality[A]: Dual[A, -[A]] =
    new Dual[A, -[A]] {
      override val rInvert: (A |*| -[A]) -⚬ One = backvert[A]
      override val lInvert: One -⚬ (-[A] |*| A) = forevert[A]
    }

  implicit val contraFunctorDemand: ContraFunctor[-] =
    new ContraFunctor[-] {
      override val category =
        coreLib.category

      override def lift[A, B](f: A -⚬ B): -[B] -⚬ -[A] =
        contrapositive(f)
    }

  def pool[A: Signaling.Positive]: LList1[A] -⚬ (Unlimited[A |*| -[A]] |*| LList1[A]) =
    coreLib.pool[A, -[A]](forevert[A])
}
