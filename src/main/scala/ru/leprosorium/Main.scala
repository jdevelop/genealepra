package ru.leprosorium

import java.io.{File, PrintWriter, FileWriter}
import java.util.concurrent.Executors

import org.apache.http.client.methods.HttpGet
import org.rogach.scallop.ScallopConf

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

object Main extends App {

  object Conf extends ScallopConf(args) {

    val threads = opt[Int]("threads", default = Some(Runtime.getRuntime.availableProcessors()))

    val output = opt[String]("output", default = Some("."))

    val users = opt[Int]("users", default = Some(1000))

  }

  val executor = Executors.newFixedThreadPool(Conf.threads())

  implicit val ctx = ExecutionContext.fromExecutor(executor)

  val baseDir = new File(Conf.output())

  require((baseDir.exists() && baseDir.isDirectory) || baseDir.mkdirs(), s"Can't proceed with ${baseDir.getAbsolutePath}")

  import Datasource._

  val chunkSize = Conf.users() / Conf.threads()

  val processes = Future.sequence(for (chunk <- 1 to Conf.users() by chunkSize) yield {
    Future {
      val writer = new FileWriter(new File(baseDir, s"lepra-$chunk"))
      for (i <- chunk to (chunk + chunkSize)) {
        SimpleProfile.getProfile(i) match {
          case None ⇒
          case Some(pr) ⇒
            println(s"Get ${pr}")
            HTTPClient.withUrl(new HttpGet(HTTPClient.encodeUrl(s"https://leprosorium.ru/users/${pr.username}"))) {
              case is ⇒
                ProfilePageParser.parse(is).foreach {
                  prfls ⇒ prfls.foreach {
                    child ⇒
                      writer.write(s"'${pr.username}' -> '${child.username}';\n")
                      if (chunk % 20 == 0) {
                        writer.flush()
                      }
                  }
                }
                Some()
            }
        }
      }
      writer.flush()
      writer.close()
      Console.err.println(s"Chunk ${chunk} complete")
    }
  })

  Await.ready(processes, 1 hour)

  sys.exit(0)

}