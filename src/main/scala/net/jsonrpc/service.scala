package perun.net.jsonrpc.service

import io.circe.Json
import io.circe.syntax.*
import sttp.client3.*
import sttp.client3.circe.*
import sttp.client3.httpclient.zio.*
import sttp.model.Uri
import zio.*

import perun.net.jsonrpc.message.{Response => PResponse, *}

trait Rpc:
  def blockchainInfo: Task[BlockchainInfo]

def live(endpoint: Uri): ZLayer[SttpClient, Nothing, Has[Rpc]] =
  ZLayer.fromService { cl =>
    new Rpc:
      def blockchainInfo: Task[BlockchainInfo] =
        quickRequest
          .post(endpoint)
          .auth
          .basic(
            "__cookie__",
            "54f3ffeaf73bb76341e40bfff09749b7e8a408e612011222f136ca15435898aa"
          )
          .body(Json.obj("jsonrpc" := "2.0", "method" := "getblockchaininfo"))
          .response(asJson[PResponse[BlockchainInfo]])
          .send(cl)
          .map(_.body)
          .absolve
          .map(_.result)
  }

def blockchainInfo: ZIO[Has[Rpc], Throwable, BlockchainInfo] =
  ZIO.accessM(_.get.blockchainInfo)
