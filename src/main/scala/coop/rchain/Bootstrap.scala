package coop.rchain

import cats.effect.{Effect, IO}
import fs2.StreamApp
import org.http4s.server.blaze.BlazeBuilder
import scala.concurrent.ExecutionContext
import api.{Play, Status, Song}
import utils.Globals._
import repo.ContractService

object Bootstrap extends StreamApp[IO] {
  import scala.concurrent.ExecutionContext.Implicits.global
  def stream(args: List[String], requestShutdown: IO[Unit]) =
    ServerStream.stream[IO]
}

object ServerStream {

  import api.middleware._

  val grpcServer = ContractService(appCfg.getInt("grpc.port"))
  val apiVersion = appCfg.getString("api.version")
  def statusApi[F[_]: Effect] = new Status[F].service
  def userApi[F[_]: Effect] = new User[F].service
  def playApi[F[_]: Effect] =   new Play[F].service 
  def songApi[F[_]: Effect] =   new Song[F].service

  def stream[F[_]: Effect](implicit ec: ExecutionContext) =
    BlazeBuilder[F]
      .bindHttp(appCfg.getInt("api.http.port"), "0.0.0.0")
      .mountService(MiddleWear(statusApi), s"/${apiVersion}/public")
      .mountService(MiddleWear(userApi), s"/${apiVersion}/user" )
      .mountService(MiddleWear(songApi), s"/${apiVersion}/song" )
      .mountService(MiddleWear(playApi), s"/${apiVersion}/play" )
      .serve
}
