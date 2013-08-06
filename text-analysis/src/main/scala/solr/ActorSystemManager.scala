package org.apache.solr.handler.tesserae

import java.util.concurrent.locks.ReentrantReadWriteLock
import akka.actor.ActorSystem
import com.typesafe.config.{ConfigValueFactory, ConfigFactory}

object ActorSystemManager {

  private var actorSystem: Option[ActorSystem] = None
  private val lock = new ReentrantReadWriteLock(true)
  private var initialized = false

  def isInitialized = {
    lock.readLock().lock()
    try {
      initialized
    } finally {
      lock.readLock().unlock()
    }
  }

  private def createSystem(): ActorSystem = {
    val cl = classOf[TesseraeCompareHandler].getClassLoader
    val config = ConfigFactory.load(cl)
      .withValue("akka.daemonic", ConfigValueFactory.fromAnyRef(true))
      .withValue("akka.jvm-exit-on-fatal-error", ConfigValueFactory.fromAnyRef(false))
      .withValue("akka.log-dead-letters", ConfigValueFactory.fromAnyRef(10))
      .withValue("akka.log-dead-letters-during-shutdown", ConfigValueFactory.fromAnyRef(false))
    ActorSystem("tesserae", config, cl)
  }

  def getActorSystem: ActorSystem = {
    lock.readLock().lock()
    try {
      actorSystem match {
        case Some(system) => system
        case None => {
          lock.readLock().unlock()
          lock.writeLock().lock()
          try {
            actorSystem match {
              case Some(system) => system
              case None => {
                val system = createSystem()
                actorSystem = Some(system)
                initialized = true
                system
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

  def shutdown() {
    lock.readLock().lock()
    try {
      if (initialized && actorSystem.isDefined) {
        lock.readLock().unlock()
        lock.writeLock().lock()

        try {
          if (initialized && actorSystem.isDefined) {
            actorSystem.get.shutdown()
            actorSystem = None
            initialized = false
          }
        } finally {
          lock.readLock().lock()
          lock.writeLock().unlock()
        }
      }
    } finally {
      lock.readLock().unlock()
    }
  }
}
