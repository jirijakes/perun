import scodec.bits.*

import zio.*
import zio.console.*

object pokus extends App:

  // 001000022200000d8000000000000000002822aaa20120f61eee3b63a380a477a063af32b2bbc97c9ff9f01f2c4225e973988108000000

  val x = perun.proto.init.init.decode(
    hex"00022200000d8000000000000000002822aaa20120f61eee3b63a380a477a063af32b2bbc97c9ff9f01f2c4225e973988108000000".toBitVector
  )

  // 0109f61eee3b63a380a477a063af32b2bbc97c9ff9f01f2c4225e97398810800000000000000ffffffff

  val res = x

  def run(args: List[String]) = putStrLn(res.toString).exitCode
