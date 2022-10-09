package perun.net.zmq

import org.zeromq.*
import scodec.bits.ByteVector
import zio.Console.*
import zio.stream.*
import zio.*

enum Message:
  case TxHash(hash: ByteVector)
  case BlockHash(hash: ByteVector)
  case Tx(tx: ByteVector)
  case Block(block: ByteVector)

trait Zmq:
  def subscribe: Stream[Throwable, Message]

def live(connect: String): ZLayer[Any, Throwable, Zmq] =
  ZLayer.scoped {
    ZIO
      .fromAutoCloseable(ZIO.succeed(new ZContext()))
      .flatMap(ctx => ZIO.attempt(ctx.createSocket(SocketType.SUB)))
      .tap(s => ZIO.attempt(s.connect(connect)))
      .tap(s => ZIO.attempt(s.subscribe(ZMQ.SUBSCRIPTION_ALL)))
      .tap(s => ZIO.attempt(s.setReceiveTimeOut(100)))
      .map { soc =>

        def get(f: ByteVector => Message) =
          (ZIO.attemptBlocking(soc.recv()) <* ZIO.attemptBlocking(soc.recv()))
            .mapBoth(Option(_), b => f(ByteVector.view(b)))

        new Zmq:
          def subscribe: Stream[Throwable, Message] =
            ZStream.fromZIOOption {
              ZIO
                .attemptBlocking(soc.recvStr())
                .mapBoth(Option(_), Option(_))
                .flatMap {
                  case Some("hashtx")    => get(Message.TxHash(_))
                  case Some("hashblock") => get(Message.BlockHash(_))
                  case Some("rawtx")     => get(Message.Tx(_))
                  case Some("rawblock")  => get(Message.Block(_))
                  case _                 => ZIO.fail(None)
                }
            }.forever
      }
  }

def subscribeZmq: ZStream[Zmq, Throwable, Message] =
  ZStream.serviceWithStream(_.subscribe)
