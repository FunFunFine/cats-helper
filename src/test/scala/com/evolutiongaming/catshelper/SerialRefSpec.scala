package com.evolutiongaming.catshelper

import cats._
import cats.arrow.FunctionK
import cats.effect.IO
import cats.implicits._
import com.evolutiongaming.catshelper.IOSuite._
import org.scalatest.AsyncFunSuite

import scala.concurrent.duration._

class SerialRefSpec extends AsyncFunSuite {

  test("modify") {
    val delay = timerIO.sleep(10.millis)

    def expect[A: Eq](t: IO[A], expected: A): IO[Unit] = {
      for {
        a <- t
        r <- {
          if (Eq[A].eqv(a, expected)) IO.unit
          else delay *> expect(t, expected)
        }
      } yield {
        r
      }
    }

    val result = for {
      ref0     <- SerialRef.of[IO, Int](0)
      ref       = ref0.mapK(FunctionK.id, FunctionK.id)
      expected  = 1000
      modifies  = List.fill(expected)(IO.shift *> ref.update(x => IO.delay { x + 1 })).parSequence
      result   <- IO.shift *> modifies.start *> expect(ref.get, expected)
    } yield result

    result.run()
  }
}