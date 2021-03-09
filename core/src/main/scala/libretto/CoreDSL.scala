package libretto

trait CoreDSL {
  /** Libretto arrow, also called a ''component'' or a ''linear function''.
    *
    * ```
    * ┏━━━━━━━━━━┓
    * ┞───┐      ┞───┐
    * ╎ A │      ╎ B │
    * ┟───┘      ┟───┘
    * ┗━━━━━━━━━━┛
    * ```
    *
    * In `A -⚬ B`, we say that the ''in-port'' is of type `A` and the ''out-port'' is of type `B`.
    * Note that the distinction between the in-port and the out-port is only formal. Information or resources
    * may flow in and out through both the in-port and the out-port.
    *
    * "Linear" means that each input is ''consumed'' exactly once, in particular, it cannot be ignored or used twice.
    */
  type -⚬[A, B]

  /** Concurrent pair. Also called a ''tensor product'' or simply ''times''. */
  type |*|[A, B]

  /** Alias for [[|*|]]. */
  type ⊗[A, B] = A |*| B

  /** No resource. It is the identity element for [[|*|]].
    * There is no flow of information through a `One`-typed port.
    */
  type One

  /** Either `A` or `B`. Analogous to [[scala.Either]].
    * Whether it is going to be `A` or `B` is decided by the producer.
    * The consumer has to be ready to handle either of the two cases.
    */
  type |+|[A, B]

  /** Alias for [[|+|]]. */
  type ⊕[A, B] = A |+| B

  /** Impossible resource. Analogous to [[Nothing]]. It is the identity element for [[|+|]]. */
  type Zero

  /** Choice between `A` and `B`.
    * The consumer chooses whether to get `A` or `B` (but can get only one of them).
    * The producer has to be ready to provide either of them.
    */
  type |&|[A, B]

  /** Signal that travels in the direction of [[-⚬]], i.e. the positive direction. */
  type Done

  /** Signal that travels in the direction opposite to [[-⚬]], i.e. the negative direction. */
  type Need

  /** A black hole that can absorb (i.e. take over the responsibility to await) [[Done]] signals, but from which there
    * is no escape.
    */
  type RTerminus

  /** A black hole that can absorb (i.e. take over the responsibility to await) [[Need]] signals, but from which there
    * is no escape.
    */
  type LTerminus

  type Rec[F[_]]

  def id[A]: A -⚬ A

  def andThen[A, B, C](f: A -⚬ B, g: B -⚬ C): A -⚬ C

  def par[A, B, C, D](
    f: A -⚬ B,
    g: C -⚬ D,
  ): (A |*| C) -⚬ (B |*| D)

  def introFst[B]: B -⚬ (One |*| B)
  def introSnd[A]: A -⚬ (A |*| One)
  def elimFst[B]: (One |*| B) -⚬ B
  def elimSnd[A]: (A |*| One) -⚬ A

  def introFst[A, X](f: One -⚬ X): A -⚬ (X |*| A) =
    andThen(introFst[A], par(f, id))

  def introSnd[A, X](f: One -⚬ X): A -⚬ (A |*| X) =
    andThen(introSnd[A], par(id, f))

  def elimFst[A, B](f: A -⚬ One): (A |*| B) -⚬ B =
    andThen(par(f, id), elimFst)

  def elimSnd[A, B](f: B -⚬ One): (A |*| B) -⚬ A =
    andThen(par(id, f), elimSnd)

  def assocLR[A, B, C]: ((A |*| B) |*| C) -⚬ (A |*| (B |*| C))
  def assocRL[A, B, C]: (A |*| (B |*| C)) -⚬ ((A |*| B) |*| C)

  def swap[A, B]: (A |*| B) -⚬ (B |*| A)

  def injectL[A, B]: A -⚬ (A |+| B)
  def injectR[A, B]: B -⚬ (A |+| B)

  def either[A, B, C](f: A -⚬ C, g: B -⚬ C): (A |+| B) -⚬ C

  def chooseL[A, B]: (A |&| B) -⚬ A
  def chooseR[A, B]: (A |&| B) -⚬ B

  def choice[A, B, C](f: A -⚬ B, g: A -⚬ C): A -⚬ (B |&| C)

  def done: One -⚬ Done
  def need: Need -⚬ One

  def delayIndefinitely: Done -⚬ RTerminus
  def regressInfinitely: LTerminus -⚬ Need

  def fork: Done -⚬ (Done |*| Done)
  def join: (Done |*| Done) -⚬ Done

  def fork[A, B](f: Done -⚬ A, g: Done -⚬ B): Done -⚬ (A |*| B) =
    andThen(fork, par(f, g))

  def join[A, B](f: A -⚬ Done, g: B -⚬ Done): (A |*| B) -⚬ Done =
    andThen(par(f, g), join)

  def forkNeed: (Need |*| Need) -⚬ Need
  def joinNeed: Need -⚬ (Need |*| Need)

  def forkNeed[A, B](f: A -⚬ Need, g: B -⚬ Need): (A |*| B) -⚬ Need =
    andThen(par(f, g), forkNeed)

  def joinNeed[A, B](f: Need -⚬ A, g: Need -⚬ B): Need -⚬ (A |*| B) =
    andThen(joinNeed, par(f, g))

  /** Signals when it is decided whether `A |+| B` actually contains the left side or the right side. */
  def signalEither[A, B]: (A |+| B) -⚬ (Done |*| (A |+| B))

  /** Signals (in the negative direction) when it is known which side of the choice (`A |&| B`) has been chosen. */
  def signalChoice[A, B]: (Need |*| (A |&| B)) -⚬ (A |&| B)

  def injectLWhenDone[A, B]: (Done |*| A) -⚬ ((Done |*| A) |+| B)
  def injectRWhenDone[A, B]: (Done |*| B) -⚬ (A |+| (Done |*| B))

  def chooseLWhenNeed[A, B]: ((Need |*| A) |&| B) -⚬ (Need |*| A)
  def chooseRWhenNeed[A, B]: (A |&| (Need |*| B)) -⚬ (Need |*| B)

  /** Factor out the factor `A` on the left of both summands. */
  def factorL[A, B, C]: ((A |*| B) |+| (A |*| C)) -⚬ (A |*| (B |+| C)) =
    either(par(id, injectL), par(id, injectR))

  /** Factor out the factor `C` on the right of both summands. */
  def factorR[A, B, C]: ((A |*| C) |+| (B |*| C)) -⚬ ((A |+| B) |*| C) =
    either(par(injectL, id), par(injectR, id))

  /** Distribute the factor on the left into the summands on the right.
    * Inverse of [[factorL]].
    */
  def distributeL[A, B, C]: (A |*| (B |+| C)) -⚬ ((A |*| B) |+| (A |*| C))

  /** Distribute the factor on the right into the summands on the left.
    * Inverse of [[factorR]].
    */
  def distributeR[A, B, C]: ((A |+| B) |*| C) -⚬ ((A |*| C) |+| (B |*| C)) =
    andThen(andThen(swap, distributeL), either(andThen(swap, injectL), andThen(swap, injectR)))

  def coFactorL[A, B, C]: (A |*| (B |&| C)) -⚬ ((A |*| B) |&| (A |*| C)) =
    choice(par(id, chooseL), par(id, chooseR))

  def coFactorR[A, B, C]: ((A |&| B) |*| C) -⚬ ((A |*| C) |&| (B |*| C)) =
    choice(par(chooseL, id), par(chooseR, id))

  /** Inverse of [[coFactorL]]. */
  def coDistributeL[A, B, C]: ((A |*| B) |&| (A |*| C)) -⚬ (A |*| (B |&| C))

  /** Inverse of [[coFactorR]]. */
  def coDistributeR[A, B, C]: ((A |*| C) |&| (B |*| C)) -⚬ ((A |&| B) |*| C) =
    andThen(andThen(choice(andThen(chooseL, swap), andThen(chooseR, swap)), coDistributeL), swap)

  /** Reverses the [[Done]] signal (flowing in the positive direction, i.e. along the `-⚬` arrow)
    * into a [[Need]] signal (flowing in the negative direciton, i.e. against the `-⚬` arrow).
    *
    * ```
    *   ┏━━━━━━━━━━━┓
    *   ┞────┐      ┃
    *   ╎Done│┄┄┐   ┃
    *   ┟────┘  ┆   ┃
    *   ┃       ┆   ┃
    *   ┞────┐  ┆   ┃
    *   ╎Need│←┄┘   ┃
    *   ┟────┘      ┃
    *   ┗━━━━━━━━━━━┛
    * ```
    */
  def rInvertSignal: (Done |*| Need) -⚬ One

  /** Reverses the [[Need]] signal (flowing in the negative direciton, i.e. against the `-⚬` arrow)
    * into a [[Done]] signal (flowing in the positive direction, i.e. along the `-⚬` arrow).
    *
    * ```
    *   ┏━━━━━━┓
    *   ┃      ┞────┐
    *   ┃   ┌┄┄╎Need│
    *   ┃   ┆  ┟────┘
    *   ┃   ┆  ┃
    *   ┃   ┆  ┞────┐
    *   ┃   └┄→╎Done│
    *   ┃      ┟────┘
    *   ┗━━━━━━┛
    * ```
    */
  def lInvertSignal: One -⚬ (Need |*| Done)

  def joinRTermini: (RTerminus |*| RTerminus) -⚬ RTerminus
  def joinLTermini: LTerminus -⚬ (LTerminus |*| LTerminus)

  def rInvertTerminus: (RTerminus |*| LTerminus) -⚬ One
  def lInvertTerminus: One -⚬ (LTerminus |*| RTerminus)

  def rec[A, B](f: (A -⚬ B) => (A -⚬ B)): A -⚬ B

  /** Hides one level of a recursive type definition. */
  def pack[F[_]]: F[Rec[F]] -⚬ Rec[F]

  /** Unpacks one level of a recursive type definition. */
  def unpack[F[_]]: Rec[F] -⚬ F[Rec[F]]

  /** Races the two [[Done]] signals and
    *  - produces left if the first signal wins, in which case it returns the second signal that still
    *    has to be awaited;
    *  - produces right if the second signal wins, in which case it returns the first signal that still
    *    has to be awaited.
    * It is biased to the left: if both signals have arrived by the time of inquiry, returns left.
    */
  def raceDone: (Done |*| Done) -⚬ (Done |+| Done)

  /** Races two [[Need]] signals, i.e. signals traveling in the negative direction (i.e. opposite the `-⚬` arrow).
    * Based on which [[Need]] signal from the out-port wins the race,
    * selects one of the two [[Need]] signals from the in-port:
    *  - If the first signal from the out-port wins the race, selects the left signal from the in-port
    *    and pipes to it the remaining (i.e. the right) signal from the out-port.
    *  - If the second signal from the out-port wins the race, selects the right signal from the in-port
    *    and pipes to it the reamining (i.e. the left) signal from the out-port.
    * It is biased to the left: if both signals from the out-port have arrived by the time of inquiry,
    * selects the left signal from the in-port.
    */
  def selectNeed: (Need |&| Need) -⚬ (Need |*| Need)
}