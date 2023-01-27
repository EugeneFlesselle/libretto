package libretto.lambda

import libretto.util.{BiInjective, Exists, Injective, Masked, TypeEq, UniqueTypeArg}
import libretto.util.TypeEq.Refl

/**
 * Binary tree with leafs holding values of types `F[X]`, `F[Y]`, ...
 * The complete structure of the tree is expressed by the type `A`, using the tags for branches and leafs.
 *
 * @tparam <*> tag for branches
 * @tparam T tag for leafs.
 * @tparam F value type of leafs. Each leaf holds a value of type `F[T]`, for some type `T`.
 * @tparam A captures the complete structure of the tree
 */
sealed trait Bin[<*>[_, _], T[_], F[_], A] {
  import Bin._

  def <*>[B](that: Bin[<*>, T, F, B]): Bin[<*>, T, F, A <*> B] =
    Branch(this, that)

  def mask: Masked[Bin[<*>, T, F, *], A] =
    Masked(this)

  def getValue[V](using
    leafIsNotBranch: [x, y, z] => (T[x] =:= (y <*> z)) => Nothing,
    T: Injective[T],
    ev: A =:= T[V],
  ): F[V] =
    ev match { case TypeEq(Refl()) => Bin.valueOf(this) }

  def mapLeafs[G[_]](f: [x] => F[x] => G[x]): Bin[<*>, T, G, A] =
    this match {
      case Leaf(a)      => Leaf(f(a))
      case Branch(l, r) => Branch(l.mapLeafs(f), r.mapLeafs(f))
    }

  def foldMap[G[_]](
    map: [x] => F[x] => G[T[x]],
    zip: [x, y] => (G[x], G[y]) => G[x <*> y],
  ): G[A] =
    this match {
      case Leaf(a)      => map(a)
      case Branch(l, r) => zip(l.foldMap(map, zip), r.foldMap(map, zip))
    }

  def foldMap0[B](
    map: [x] => F[x] => B,
    reduce: (B, B) => B,
  ): B = {
    type G[x] = B
    foldMap[G](map, [x, y] => (x: G[x], y: G[y]) => reduce(x, y))
  }

  def deduplicateLeafs[->[_, _]](
    dup: [x] => F[x] => T[x] -> (T[x] <*> T[x]),
  )(using
    leafTest: UniqueTypeArg[F],
    shuffled: Shuffled[->, <*>],
  ): Exists[[X] =>> (Bin[<*>, T, F, X], shuffled.Shuffled[X, A])] =
    this match {
      case l @ Leaf(_) =>
        Exists((l, shuffled.id))
      case Branch(l, r) =>
        (l.deduplicateLeafs(dup), r.deduplicateLeafs(dup)) match
          case (Exists.Some((x1, f1)), Exists.Some((x2, f2))) =>
            (x1 mergeIn x2)(dup) match
              case Exists.Some((x, f)) =>
                Exists((x, f > shuffled.par(f1, f2)))
    }

  private def mergeIn[B, ->[_, _]](that: Bin[<*>, T, F, B])(
    dup: [x] => F[x] => T[x] -> (T[x] <*> T[x]),
  )(using
    leafTest: UniqueTypeArg[F],
    shuffled: Shuffled[->, <*>],
  ): Exists[[X] =>> (Bin[<*>, T, F, X], shuffled.Shuffled[X, A <*> B])] =
    (this mergeIn0 that)(dup) match {
      case MergeRes.Absorbed(x, f) =>
        Exists((x, f))
      case MergeRes.WithRemainder(x, r, f1, g) =>
        Exists((Branch(x, r), shuffled.fst(f1) > shuffled.Pure(g)))
    }

  private def mergeIn0[B, ->[_, _]](that: Bin[<*>, T, F, B])(
    dup: [x] => F[x] => T[x] -> (T[x] <*> T[x]),
  )(using
    leafTest: UniqueTypeArg[F],
    shuffled: Shuffled[->, <*>],
  ): MergeRes[A, B, shuffled.shuffle.~⚬, shuffled.Shuffled] = {
    import MergeRes.{Absorbed, WithRemainder}
    import shuffled.{assocRL, fst, id, ix, lift, par, pure, swap, xi}
    import shuffled.shuffle.~⚬

    given shuffled.shuffle.type = shuffled.shuffle

    this match {
      case l: Leaf[br, t, f, a] =>
        that.find(l.value) match
          case that.FindRes.Total(TypeEq(Refl())) =>
            Absorbed(l, lift(dup(l.value)))
          case that.FindRes.Partial(r, f) =>
            WithRemainder(l, r, lift(dup(l.value)), ~⚬.assocLR > f.inSnd)
          case that.FindRes.NotFound() =>
            WithRemainder(this, that, id, ~⚬.id)
      case br: Branch[br, t, f, a1, a2] =>
        (br.l mergeIn0 that)(dup) match
          case br.l.MergeRes.Absorbed(p, f) =>
            Absorbed(Branch(p, br.r), f.inFst[a2] > ix)
          case br.l.MergeRes.WithRemainder(p, r, f1, g) =>
            (br.r mergeIn0 r)(dup) match
              case br.r.MergeRes.Absorbed(p1, f2) =>
                Absorbed(Branch(p, p1), par(f1, f2) > xi > pure(g).inSnd > assocRL > fst(swap))
              case br.r.MergeRes.WithRemainder(p1, r1, f2, g2) =>
                WithRemainder(Branch(p, p1), r1, par(f1, f2), ~⚬.assocLR > ~⚬.snd(g2) > ~⚬.xi > ~⚬.snd(g) > ~⚬.assocRL > ~⚬.fst(~⚬.swap))
    }
  }

  private enum MergeRes[A, B, -->[_, _], ==>[_, _]] {
    case Absorbed[P, A, B, -->[_, _], ==>[_, _]](
      p: Bin[<*>, T, F, P],
      f: P ==> (A <*> B),
    ) extends MergeRes[A, B, -->, ==>]

    case WithRemainder[P, Q, R, A, B, -->[_, _], ==>[_, _]](
      p: Bin[<*>, T, F, P],
      r: Bin[<*>, T, F, R],
      f1: P ==> Q,
      g: (Q <*> R) --> (A <*> B),
    ) extends MergeRes[A, B, -->, ==>]
  }

  def product[B](that: Bin[<*>, T, F, B])(using
    leafTest: UniqueTypeArg[F],
    shuffle: Shuffle[<*>],
  ): Exists[[X] =>> (
    Bin[<*>, T, F, X],
    Exists[[A0] =>> (Projection[<*>, X, A0], shuffle.~⚬[A0, A])],
    Exists[[B0] =>> (Projection[<*>, X, B0], shuffle.~⚬[B0, B])],
  )] =
    (this product0 that) match {
      case ProductRes.Absorbed(x, p1, p2, f1, f2) =>
        Exists((x, Exists(p1, f1), Exists(p2, f2)))
      case ProductRes.WithRemainder(x, r, p1, p2, f1, f2) =>
        Exists((
          Branch(x, r),
          Exists((Projection.discardSnd > p1, f1)),
          Exists((p2, f2)),
        ))
    }

  private def product0[B](that: Bin[<*>, T, F, B])(using
    leafTest: UniqueTypeArg[F],
    shuffle: Shuffle[<*>],
  ): ProductRes[A, B, shuffle.~⚬] = {
    import ProductRes.{Absorbed, WithRemainder}
    import shuffle.~⚬

    this match {
      case l: Leaf[br, t, f, a] =>
        that.find(l.value) match
          case that.FindRes.Total(TypeEq(Refl())) =>
            Absorbed(l, Projection.id, Projection.id, ~⚬.id, ~⚬.id)
          case that.FindRes.Partial(r, f) =>
            WithRemainder(l, r, Projection.id, Projection.id, ~⚬.id, f)
          case that.FindRes.NotFound() =>
            WithRemainder(l, that, Projection.id, Projection.discardFst, ~⚬.id, ~⚬.id)
      case br: Branch[br, t, f, a1, a2] =>
        (br.l product0 that) match
          case br.l.ProductRes.Absorbed(p, p1, p2, f1, f2) =>
            Absorbed(Branch(p, br.r), p1.inFst, Projection.discardSnd(p2), f1.inFst, f2)
          case br.l.ProductRes.WithRemainder(p, r, p1, p2, f1, f2) =>
            (br.r product0 r) match
              case br.r.ProductRes.Absorbed(q, q1, q2, g1, g2) =>
                g2.inSnd.project(p2) match
                  case ~⚬.ProjectRes.Projected(p2, g2) =>
                    Absorbed(Branch(p, q), Projection.par(p1, q1), q2.inSnd > p2, ~⚬.par(f1, g1), g2 > f2)
              case br.r.ProductRes.WithRemainder(q, r, q1, q2, g1, g2) =>
                g2.inSnd.project(p2) match
                  case ~⚬.ProjectRes.Projected(p2, g2) =>
                    ~⚬.assocLR.project(q2.inSnd > p2) match
                      case ~⚬.ProjectRes.Projected(p2, h2) =>
                        WithRemainder(Branch(p, q), r, Projection.par(p1, q1), p2, ~⚬.par(f1, g1), h2 > g2 > f2)
    }
  }

  private enum ProductRes[A, B, -->[_, _]] {
    case Absorbed[P, A0, B0, A, B, -->[_, _]](
      p: Bin[<*>, T, F, P],
      p1: Projection[<*>, P, A0],
      p2: Projection[<*>, P, B0],
      f1: A0 --> A,
      f2: B0 --> B,
    ) extends ProductRes[A, B, -->]

    case WithRemainder[P, R, A0, B0, A, B, -->[_, _]](
      p: Bin[<*>, T, F, P],
      r: Bin[<*>, T, F, R],
      p1: Projection[<*>, P, A0],
      p2: Projection[<*>, P <*> R, B0],
      f1: A0 --> A,
      f2: B0 --> B,
    ) extends ProductRes[A, B, -->]
  }

  private def find[X](x: F[X])(using
    leafTest: UniqueTypeArg[F],
    shuffle: Shuffle[<*>],
  ): FindRes[A, X, shuffle.~⚬] = {
    import FindRes._
    import shuffle.~⚬

    this match {
      case Leaf(a) =>
        leafTest.testEqual(a, x) match
          case Some(ev) => Total(ev.liftCo[T])
          case None     => NotFound()
      case Branch(l, r) =>
        l.find(x) match
          case l.FindRes.Total(TypeEq(Refl())) =>
            Partial(r, ~⚬.id)
          case l.FindRes.Partial(q, f) =>
            Partial(Branch(q, r), ~⚬.assocRL > f.inFst)
          case l.FindRes.NotFound() =>
            r.find(x) match
              case r.FindRes.Total(TypeEq(Refl())) =>
                Partial(l, ~⚬.swap)
              case r.FindRes.Partial(q, f) =>
                Partial(Branch(l, q), ~⚬.xi > ~⚬.snd(f))
              case r.FindRes.NotFound() =>
                NotFound()
    }
  }

  private enum FindRes[A, X, -->[_, _]] {
    case NotFound()
    case Total(ev: A =:= T[X])

    case Partial[R, A, X, -->[_, _]](
      remainder: Bin[<*>, T, F, R],
      f: (T[X] <*> R) --> A
    ) extends FindRes[A, X, -->]
  }
}

object Bin {
  case class Branch[<*>[_, _], T[_], F[_], A, B](l: Bin[<*>, T, F, A], r: Bin[<*>, T, F, B]) extends Bin[<*>, T, F, A <*> B]
  case class Leaf[<*>[_, _], T[_], F[_], A](value: F[A]) extends Bin[<*>, T, F, T[A]]

  def branchesOf[<*>[_, _], T[_], F[_], A, B](tree: Bin[<*>, T, F, A <*> B])(using
    leafIsNotBranch: [x, y, z] => (T[x] =:= (y <*> z)) => Nothing,
  )(using
    BiInjective[<*>],
  ): (Bin[<*>, T, F, A], Bin[<*>, T, F, B]) =
    tree.mask.visit[(Bin[<*>, T, F, A], Bin[<*>, T, F, B])](
      [X] => (tree: Bin[<*>, T, F, X], ev: X =:= (A <*> B)) =>
        tree match {
          case t: Branch[br, lf, f, a, b] =>
            (summon[(a <*> b) =:= X] andThen ev) match
              case BiInjective[<*>](TypeEq(Refl()), TypeEq(Refl())) =>
                (t.l, t.r)
          case _: Leaf[br, lf, f, v] =>
            leafIsNotBranch[v, A, B](ev)
        }
    )

  def valueOf[<*>[_, _], T[_], F[_], A](tree: Bin[<*>, T, F, T[A]])(using
    leafIsNotBranch: [x, y, z] => (T[x] =:= (y <*> z)) => Nothing,
  )(using
    Injective[T],
  ): F[A] =
    tree.mask.visit[F[A]](
      [X] => (tree: Bin[<*>, T, F, X], ev: X =:= T[A]) =>
        tree match
          case l: Leaf[br, t, f, x] =>
            (summon[T[x] =:= X] andThen ev) match
              case Injective[T](TypeEq(Refl())) =>
                l.value
          case _: Branch[br, t, f, a, b] => leafIsNotBranch[A, a, b](ev.flip)
    )

  given [<*>[_, _], T[_], F[_]](using
    leafIsNotBranch: [x, y, z] => (T[x] =:= (y <*> z)) => Nothing,
  )(using
    BiInjective[<*>],
  ): Cartesian[<*>, Bin[<*>, T, F, *]] with {
    override def zip[A, B](a: Bin[<*>, T, F, A], b: Bin[<*>, T, F, B]): Bin[<*>, T, F, A <*> B] =
      Branch(a, b)

    override def unzip[A, B](ab: Bin[<*>, T, F, A <*> B]): (Bin[<*>, T, F, A], Bin[<*>, T, F, B]) =
      branchesOf(ab)
  }
}
