package org.apache.solr.handler.tesserae

import java.util.concurrent.locks.ReentrantReadWriteLock
import akka.actor._
import scala.collection.{GenTraversable, mutable}
import scala.util.Random
import akka.actor.Terminated
import java.util.UUID
import scala.concurrent.Await
import org.slf4j.LoggerFactory

final case class Job[A, B](recipient: ActorRef, worker: (A) => B, work: A, position: Int)
final case class JobResult[T](result: Either[T, Throwable], position: Int)
final case class RegisterWorker(worker: ActorRef)

final case class BeginParallelMap[A, B](traverseable: GenTraversable[A], worker: (A) => B)
final case class ParallelMapResult[A](result: List[Either[A, Throwable]])

case object GimmeWork

object GenericWorker {

  private val lock = new ReentrantReadWriteLock(true)
  private lazy val logger = LoggerFactory.getLogger(getClass)
  private var initialized = false
  private var master: Option[ActorRef] = None
  private var workers: Set[ActorRef] = Set.empty
  private var numWorkers = (Runtime.getRuntime.availableProcessors() * 2) + 1

  protected class Master extends Actor {
    val availableWorkers = mutable.Set.empty[ActorRef]
    val idleWorkers = mutable.Set.empty[ActorRef]
    var unprocessedJobs: List[Job[_, _]] = Nil

    def receive = {
      case job: Job[_, _] =>
        if (availableWorkers.isEmpty) {
          unprocessedJobs = unprocessedJobs :+ job
        } else {
          Random.shuffle(idleWorkers).toList match {
            case Nil => unprocessedJobs = unprocessedJobs :+ job
            case worker :: _ =>
              worker ! job
              idleWorkers.remove(worker)
          }
        }
      case RegisterWorker(worker) =>
        context.watch(worker)
        availableWorkers += worker
      case Terminated(worker) =>
        availableWorkers.remove(worker)
        idleWorkers.remove(worker)
      case GimmeWork =>
        unprocessedJobs match {
          case Nil =>
            idleWorkers += sender
          case head :: tail =>
            sender ! head
            unprocessedJobs = tail
        }
    }
  }

  protected class Worker(val master: ActorRef) extends Actor {
    override def preStart() {
      master ! RegisterWorker(self)
      master ! GimmeWork // keep working on actor restart
    }

    def receive = {
      case job: Job[Any, Any] =>
        val result: Either[Any, Throwable] = try {
          Left(job.worker(job.work))
        } catch {
          case err: Throwable => Right(err)
        }

        job.recipient ! JobResult(result, job.position)
        master ! GimmeWork
    }
  }

  protected class ParallelMapper[A, B](val master: ActorRef) extends Actor {
    private var results: mutable.Map[Int, Either[B, Throwable]] = mutable.HashMap.empty
    private var outstandingIndexes: mutable.Set[Int] = mutable.HashSet.empty
    private var originator: Option[ActorRef] = None

    def receive = {
      case BeginParallelMap(traverseable: A, worker: (A => B)) =>
        originator = Some(sender)
        var index = 0
        traverseable.foreach { item =>
          master ! Job(self, worker, item, index)
          outstandingIndexes += index
          index += 1
        }

        if (index == 0) {
          sender ! ParallelMapResult(Nil)
        }
      case JobResult(result: Either[B, Throwable], position: Int) =>
        if (!outstandingIndexes.contains(position)) {
          // TODO: Log a message
        } else {
          outstandingIndexes -= position
          results += position -> result
        }

        if (outstandingIndexes.isEmpty) {
          val size = results.size
          var list: List[Either[B, Throwable]] = Nil
          for (i <- 0 until size) {
            list = list :+ results(i)
          }

          originator.map { origSender =>
            origSender ! ParallelMapResult(list)
          }
        }
    }
  }

  def isInitialized = {
    lock.readLock().lock()
    try {
      initialized
    } finally {
      lock.readLock().unlock()
    }
  }

  def getNumWorkers = {
    lock.readLock().lock()
    try {
      numWorkers
    } finally {
      lock.readLock().unlock()
    }
  }

  def setNumWorkers(num: Int) {
    if (num <= 0) {
      throw new IllegalArgumentException("Number of workers must be positive")
    }

    lock.readLock().lock()
    try {
      if (initialized) {
        throw new IllegalStateException("Can't change the number of workers, they've already been created")
      }

      lock.readLock().unlock()
      lock.writeLock().lock()
      try {
        if (initialized) {
          throw new IllegalStateException("Can't change the number of workers, they've already been created")
        }
        numWorkers = num
      } finally {
        lock.readLock().lock()
        lock.writeLock().unlock()
      }
    } finally {
      lock.readLock().unlock()
    }
  }

  def getMaster: ActorRef = {
    lock.readLock().lock()
    try {
      master match {
        case Some(ref) => ref
        case None => {
          lock.readLock().unlock()
          lock.writeLock().lock()
          try {
            master match {
              case Some(ref) => ref
              case None => {
                val system = ActorSystemManager.getActorSystem
                val ref = system.actorOf(Props(new Master), name="generic-worker-master")

                for (i <- 0 until numWorkers) {
                  val num = i + 1
                  val worker = system.actorOf(Props(new Worker(ref)), name="generic-worker-" + num)
                  workers += worker
                }

                master = Some(ref)
                logger.info("Initialized Tesserae worker system with " + numWorkers + " slaves")
                initialized = true
                ref
              }
            }
          } finally {
            lock.readLock().lock()
            lock.writeLock().unlock()
          }
        }
      }
    } finally {
      lock.readLock().unlock()
    }
  }

  class GenTraverseableParallelMapper[A](traverseable: GenTraversable[A]) {
    def parallelMap[B](f: (A) => B): List[B] = {
      if (getNumWorkers == 1) {
        return traverseable.map(f).toList
      }

      import scala.concurrent.duration._
      import akka.pattern.ask
      import akka.util.Timeout

      val master = getMaster
      val system = ActorSystemManager.getActorSystem
      val actorId = UUID.randomUUID().toString
      val mapper = system.actorOf(Props(new ParallelMapper(master)), name="parallel-mapper-" + actorId)

      try {
        implicit val timeout = Timeout(36500 days)
        val future = mapper ? BeginParallelMap(traverseable, f)
        val result = Await.result(future, Duration.Inf)
        result match {
          case t: Throwable => throw t
          case pmr: ParallelMapResult[B] =>
            pmr.result.map { entry =>
              entry match {
                case Left(b: B) => b
                case Right(t: Throwable) => throw t
              }
            }
          case o =>
            throw new IllegalStateException("Unexpected result from parallel mapper: " + o.getClass)
        }
      } finally {
        mapper ! PoisonPill
      }
    }
  }

  implicit def makeTraverseableParallel[A](traverseable: GenTraversable[A]) =
    new GenTraverseableParallelMapper[A](traverseable)
}
