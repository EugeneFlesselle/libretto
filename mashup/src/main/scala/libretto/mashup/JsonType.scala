package libretto.mashup

import libretto.mashup.dsl._

/** Witnesses that values of type [[A]] are directly representable in JSON. */
sealed trait JsonType[A]

object JsonType {
  case object JsonTypeText extends JsonType[Text]

  case object JsonTypeFloat64 extends JsonType[Float64]

  case object JsonTypeEmptyRecord extends JsonType[Record]

  case class JsonTypeRecord[A, Name <: String, T](
    init: JsonType[A],
    name: Name,
    typ: JsonType[T],
  ) extends JsonType[A ## (Name of T)]

  given jsonTypeText: JsonType[Text] =
    JsonTypeText

  given jsonTypeFloat64: JsonType[Float64] =
    JsonTypeFloat64

  given jsonTypeEmptyRecord: JsonType[Record] =
    JsonTypeEmptyRecord

  given jsonTypeRecord[A, Name <: String, T](using
    A: JsonType[A],
    N: ConstValue[Name],
    T: JsonType[T],
  ): JsonType[A ## (Name of T)] =
    JsonTypeRecord(A, N.value, T)
}