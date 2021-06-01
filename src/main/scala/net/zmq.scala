package perun.net.zmq

import org.zeromq.*
import scodec.bits.ByteVector
import zio.blocking.*
import zio.console.*
import zio.stream.*
import zio.{blocking as _, *}

enum Message:
  case TxHash(hash: ByteVector)
  case BlockHash(hash: ByteVector)
  case Tx(tx: ByteVector)
  case Block(block: ByteVector)

trait Zmq:
  def subscribe: Stream[Throwable, Message]

def live(connect: String): ZLayer[Blocking, Throwable, Has[Zmq]] =
  ZManaged
    .fromAutoCloseable(UIO(new ZContext()))
    .mapM(ctx => Task(ctx.createSocket(SocketType.SUB)))
    .tapM(s => Task(s.connect(connect)))
    .tapM(s => Task(s.subscribe(ZMQ.SUBSCRIPTION_ALL)))
    .tapM(s => Task(s.setReceiveTimeOut(100)))
    .zip(ZManaged.environment[Blocking])
    .map { (soc, blk) =>

      def get(f: ByteVector => Message) =
        blocking(Task(soc.recv()) <* Task(soc.recv()))
          .bimap(Option(_), b => f(ByteVector.view(b)))

      new Zmq:
        def subscribe: Stream[Throwable, Message] =
          Stream
            .fromEffectOption {
              blocking(Task(soc.recvStr()))
                .bimap(Option(_), Option(_))
                .flatMap {
                  case Some("hashtx")    => get(Message.TxHash(_))
                  case Some("hashblock") => get(Message.BlockHash(_))
                  case Some("rawtx")     => get(Message.Tx(_))
                  case Some("rawblock")  => get(Message.Block(_))
                  case _                 => ZIO.fail(None)
                }
            }
            .forever
            .provide(blk)
    }
    .toLayer

def subscribeZmq: ZStream[Has[Zmq], Throwable, Message] =
  ZStream.accessStream(_.get.subscribe)
