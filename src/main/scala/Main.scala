import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import akka.stream.contrib.SwitchMode
import akka.stream.contrib.Valve
import akka.stream.contrib.ValveSwitch
import akka.stream.scaladsl.BroadcastHub
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.ws.DefaultBodyWritables.writableOf_Source
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Main extends App {

  implicit val actorSystem: ActorSystem = ActorSystem("parallel")
  implicit val materializer: SystemMaterializer = SystemMaterializer(actorSystem)

  val source = Source.fromIterator(() => Range(1, 1000).toIterator)

  def createSources(root: Source[Int, _])(implicit materializer: Materializer): (Future[ValveSwitch], Source[Int, _]) = {
    // First, attach the valve and retrieve it via pre-materialisation - we create it CLOSED so that we don't start
    // pushing the stream as soon as we attach the first subscriber
    val preMatRoot: (Future[ValveSwitch], Source[Int, NotUsed]) = root.viaMat(Valve(SwitchMode.Close))(Keep.right).preMaterialize()

    // Now we attach the returned source to a Broadcast Hub, which creates a source that we'll attach to
    // our multiple subscribers
    //
    // This buffer size can be whatever, I just took this from an example.
    (preMatRoot._1, preMatRoot._2.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right).run())
  }

  def run(source: Source[Int, _]): Future[Unit] = {
    // Create the source, also returning the valve that we need to open once we're ready to go.
    val (switch, sourceToUse) = createSources(source)

    val ws = StandaloneAhcWSClient()

    // We attach our subscribers, but they won't be getting any data yet. They are materialised independently,
    // which is perfect for attaching to WS/HMRC Verbs
    val source1: Source[ByteString, _] = sourceToUse.fold(0)(_ + _).via(Flow.fromFunction((x: Int) => ByteString(x.toString)))
    val source2: Source[ByteString, _] = sourceToUse.fold(0)((current, _) => current + 1).via(Flow.fromFunction((x: Int) => ByteString(x.toString)))

    // need to be done in parallel so must be outside the for comprehension
    val call1 = ws.url("https://828kz.mocklab.io/identity").withBody(source1).execute("POST")
    val call2 = ws.url("https://828kz.mocklab.io/identity2").withBody(source2).execute("POST")

    val future = for {
      f1 <- call1
      f2 <- call2
    } yield (f1.body, f2.body)

    // Now we've attached all our sources, we'll open the valve
    println("Pre valve opening")
    Thread.sleep(2000) // for demonstration purposes.
    switch.map { s =>
      println("Valve opening")
      s.flip(SwitchMode.Open)
      println("Valve opened")
    }

    future.map {
      result =>
        println("Results")
        println(result._1)
        println(result._2)
    }
  }

  // Run and then terminate the actor system
  run(source).flatMap(_ => actorSystem.terminate())

}