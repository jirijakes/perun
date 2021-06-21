package perun.proto.codecs

import scodec.bits.{BitVector, hex}
import scodec.{Attempt, DecodeResult, Err}
import zio.*
import zio.test.*

object test extends DefaultRunnableSpec:

  import perun.proto.uint64.*

  import Assertion.*

  val spec =
    suite("bigsize")(
      suite("encoding")(
        testM("BOLT #1 testing vector") {
          val gen = Gen.fromIterable(
            List(
              (0L, hex"00"),
              (252L, hex"fc"),
              (253L, hex"fd00fd"),
              (65535L, hex"fdffff"),
              (65536L, hex"fe00010000"),
              (4294967295L, hex"feffffffff"),
              (4294967296L, hex"ff0000000100000000")
              // (18446744073709551615L, "ffffffffffffffffff")
            ).map((i, s) => (i, s.toBitVector))
          )

          checkAll(gen) { (l, b) =>
            assert(bigsize.encode(l))(equalTo(Attempt.Successful(b)))
          }
        }
      ),
      suite("decoding")(
        suite("BOLT #1 testing vector")(
          testM("success") {
            val gen = Gen.fromIterable(
              List(
                (hex"00", 0L),
                (hex"fc", 252L),
                (hex"fd00fd", 253L),
                (hex"fdffff", 65535L),
                (hex"fe00010000", 65536L),
                (hex"feffffffff", 4294967295L),
                (hex"ff0000000100000000", 4294967296L)
                // ("ffffffffffffffffff", UInt64(184467440737095516L))
              ).map((s, i) => (s.toBitVector, i))
            )

            checkAll(gen) { (b, l) =>
              assert(bigsize.decodeValue(b))(
                equalTo(Attempt.Successful(l))
              )
            }
          },
          testM("failure – not canonical") {
            val gen = Gen.fromIterable(
              List(hex"fd00fc", hex"fe0000ffff", hex"ff00000000ffffffff")
                .map(_.toBitVector)
            )

            checkAll(gen) { b =>
              assert(bigsize.decode(b))(
                equalTo(Attempt.Failure(Err("value was not minimally encoded")))
              )
            }
          },
          testM("failure – not enough bits") {
            val gen = Gen
              .fromIterable(
                List(
                  hex"fd00",
                  hex"feffff",
                  hex"ffffffffff",
                  hex"",
                  hex"fd",
                  hex"fe",
                  hex"ff"
                )
              )
              .map(_.toBitVector)

            checkAll(gen) { b =>
              assert(bigsize.decode(b))(
                hasField(
                  "",
                  _.fold(_.message, _ => "OK"),
                  startsWithString("cannot acquire")
                )
              )
            }
          }
        )
      )
    )
