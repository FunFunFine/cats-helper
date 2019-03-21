package com.evolutiongaming.catshelper

import java.util.concurrent.Executors

import cats.arrow.FunctionK
import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import cats.temp.par._
import org.scalatest.{AsyncFunSuite, Matchers}
import com.evolutiongaming.catshelper.IOSuite._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

class ThreadLocalRefSpec extends AsyncFunSuite with Matchers {

  test("thread local stored per thread") {
    val result = testF[IO](5).run()
    Thread.sleep(10000)
    result
  }

  private def testF[F[_] : Sync : ThreadLocalOf : Par : Clock : ContextShift](n: Int): F[Unit] = {

    def test(ref: ThreadLocalRef[F, String], executor: ExecutionContext) = {

      val get = ref.get

      val check = {
        for {
          a0 <- get
          a1 <- get
          _   = a0 shouldEqual a1
        } yield a0
      }

      for {
        a  <- check
        a1 <- ContextShift[F].evalOn(executor)(get)
        _   = a should not equal a1
        _  <- ref.set(a + "|")
        _  <- check
        _  <- ref.update(_ + "|")
        _  <- check
        _  <- ref.modify(a => (a + "|", ()))
        _  <- check
      } yield a
    }

    def executor(parallelism: Int): Resource[F, ExecutionContextExecutorService] = {
      val result = Sync[F].delay {
        val es = Executors.newFixedThreadPool(parallelism)
        val ec = ExecutionContext.fromExecutorService(es)
        val release = Sync[F].delay { ec.shutdown() }
        (ec, release)
      }
      Resource(result)
    }

    executor(parallelism = n).use { executor =>

      for {
        counter     <- Ref[F].of(0)
        thread       = for {
          _        <- counter.update(_ + 1)
          thread   <- Sync[F].delay { Thread.currentThread().toString }
        } yield thread
        threadLocal  <- ThreadLocalOf[F].apply(thread)
        threadLocal1  = threadLocal.mapK(FunctionK.id)
        a             = test(threadLocal1, executor)
        treadIds     <- List.fill(n)(a).parSequence
        counter      <- counter.get
      } yield {
        val size = treadIds.distinct.size
        size should be > 1
        counter should be >= size
        ()
      }
    }
  }
}
