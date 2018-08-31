package coop.rchain.repo

import java.io.StringReader
import coop.rchain.casper.protocol._
import coop.rchain.domain.{Err, ErrorCode}
import com.google.protobuf.empty._
import coop.rchain.models.Channel.ChannelInstance.Quote
import coop.rchain.models.{Channel, Par}
import coop.rchain.models.Expr.ExprInstance.GString
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import coop.rchain.domain._
import coop.rchain.domain.ErrorCode._
import coop.rchain.rholang.interpreter._
import com.typesafe.scalalogging.Logger
import coop.rchain.models.rholang.implicits._
import scala.util._
import coop.rchain.utils.Globals._

object RholangProxy {

  lazy val (host, port) =
    (appCfg.getString("grpc.host"), appCfg.getInt("grpc.ports.external"))

  def apply(channel: ManagedChannel): RholangProxy =
    new RholangProxy(channel)

  def apply(host: String, port: Int): RholangProxy = {
    val channel =
      ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build
    new RholangProxy(channel)
  }

  def parsToChannel(pars: Seq[Par]): Channel = {
    val p = pars.foldLeft(new Par())(_ ++ _)
    Channel(Quote(p))
  }

  def parToChannel(par: Par): Channel = Channel(Quote(par))

  def asPar(name: String): Either[Err, Par] = {
    val par = Interpreter.buildNormalizedTerm(new StringReader(name)).runAttempt
    par match {
      case Left(e)  => Left(Err(nameToPar, e.getMessage, None))
      case Right(r) => Right(r)
    }
  }
}

class RholangProxy(channel: ManagedChannel) {
  import RholangProxy._

  private lazy val grpc = DeployServiceGrpc.blockingStub(channel)
  private lazy val log = Logger[RholangProxy]

  def shutdown = channel.shutdownNow()

  def deploy(contract: String) = {
    val resp = grpc.doDeploy(
      DeployData()
        .withTerm(contract)
        .withTimestamp(System.currentTimeMillis())
        .withPhloLimit(0)
        .withPhloPrice(0)
        .withNonce(0)
        .withFrom("0x1")
    )

    if (resp.success)
      Right(resp.message)
    else Left(Err(ErrorCode.grpcDeploy, resp.message, Some(contract)))
  }

  val deployFromFile: String => Either[Err, String] = path =>
    for {
      c <- immersionConstract(path)
      d <- deploy(c)
    } yield d

  def showBlocks =
    grpc.showBlocks(Empty()).toList

  def proposeBlock = {
    val response: DeployServiceResponse = grpc.createBlock(Empty())
    response.success match {
      case true =>
        Right(response.message)
      case false => Left(Err(ErrorCode.grpcPropose, response.message, None))
    }
  }

  def deployAndPropose(contract: String) =
    for {
      d <- deploy(contract)
      p <- proposeBlock
    } yield DeployAndProposeResponse(d, p)

  def dataAtContWithTerm(
      name: String): Either[Err, ListeningNameContinuationResponse] = {
    val par = Interpreter.buildNormalizedTerm(new StringReader(name)).runAttempt
    par.map(p => dataAtCont(p)) match {
      case Left(e)  => Left(Err(nameToPar, e.getMessage, None))
      case Right(r) => Right(r)
    }
  }

  def dataAtNameWithTerm(
      name: String): Either[Err, ListeningNameDataResponse] = {
    val par = Interpreter.buildNormalizedTerm(new StringReader(name)).runAttempt
    par.map(p => dataAtName(p)) match {
      case Left(e)  => Left(Err(nameToPar, e.getMessage, None))
      case Right(r) => r
    }
  }

  def dataAtName(name: String): Either[Err, ListeningNameDataResponse] = {
    val par: Par = GString(name)
    dataAtName(par)
  }

  def dataAtName(par: Par): Either[Err, ListeningNameDataResponse] = {
    val res = (parToChannel _ andThen grpc.listenForDataAtName _)(par)
    res.status match {
      case "Success" => Right(res)
      case _ =>
        Left(Err(ErrorCode.nameNotFount, s"no data for par: ${par}", None))
    }
  }

  def dataAtCont(par: Par) = {
    val ch: Channel = Channel(Quote(par))
    grpc.listenForContinuationAtName(Channels(Seq(ch)))
  }

  val immersionConstract: String => Either[Err, String] = fileName => {
    val stream = getClass.getResourceAsStream(fileName)
    Try(
      scala.io.Source.fromInputStream(stream).getLines.reduce(_ + _ + "\n")
    ) match {
      case Success(s) =>
        stream.close
        Right(s)
      case Failure(e) =>
        stream.close
        Left(Err(ErrorCode.contractFile, fileName, None))
    }
  }

  val propose: String => Either[Err, DeployAndProposeResponse] = deployResp =>
    proposeBlock map (x =>
      DeployAndProposeResponse(fromDeploy = deployResp, fromPropose = x))
}
