package com.webchat.main

/**
  * Created by raitis on 11/07/2016.
  *
  */

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream._
import akka.stream.scaladsl._


/**
  * Case classes, chat events (model).
  *
  */
sealed trait ChatEvent
case class ChatMessage(user: String, message: String) extends ChatEvent
case class Login(name: String, actorRef: ActorRef) extends ChatEvent
case class Logout(name: String) extends ChatEvent


/**
  * Chat rooms vector and allocation for chat room, according to Uri parameters
  *
  */
object Chat {

  var chatRooms: Vector[ChatRoom] = Vector.empty

  def findRoom(roomName: String)(implicit actorSystem: ActorSystem): ChatRoom = {
    chatRooms.find(room => room.roomName == roomName)
      .getOrElse({
        val chatRoom = ChatRoom(roomName, actorSystem)
        chatRooms = chatRooms :+ chatRoom
        chatRoom
      })
  }

}


/**
  * Chat room. Actor and Flow.
  *
  */
case class ChatRoom(roomName: String, actorSystem: ActorSystem) {

  // actor
  private val chatRoomActor = actorSystem.actorOf(Props(classOf[ChatRoomActor]))

  // flow
  def stream(userName: String): Flow[Message, Message, _] = {

    Flow.fromGraph(GraphDSL.create(Source.actorRef[ChatMessage](10, OverflowStrategy.fail)) {

      import GraphDSL.Implicits._

      implicit builder =>

        // to access GraphDSL.create(Source.actorRef[ChatMessage](10, OverflowStrategy.fail) as parameter
        source =>

          //flow used as input, it takes Messages
          val fromWebsocket = builder.add(
            Flow[Message].collect {
              case TextMessage.Strict(txt) => ChatMessage(userName, txt)
            }
          )


          //flow used as output, it returns Messages
          val backToWebsocket = builder.add(
            Flow[ChatMessage].map {
              case ChatMessage(username, text) => TextMessage(s"$username: [$text]")
            }
          )

          // actor - get actorRef for ws connection
          val materialized = builder.materializedValue.map(actor => Login(userName, actor))
          // sink a flow to actor (chatRoomActor)
          val sink = Sink.actorRef[ChatEvent](chatRoomActor, Logout(userName))

          val merge = builder.add(Merge[ChatEvent](2))

          fromWebsocket ~> merge.in(0)
          materialized ~> merge.in(1)
          merge ~> sink

          source ~> backToWebsocket

          FlowShape(fromWebsocket.in, backToWebsocket.out)
    })

  }
}


/**
  * Chat room actor definition
  *
  */
class ChatRoomActor extends Actor {

  var participiants: Map[String, ActorRef] = Map.empty

  override def receive: Receive = {
    case Login(name, actorRef) => {
      participiants += name -> actorRef
      broadcast(ChatMessage("System", s"User $name joined"))
    }
    case Logout(name) => {
      participiants -= name
      broadcast(ChatMessage("System", s"User $name left"))
    }
    case msg: ChatMessage => broadcast(msg)
    case _ => println("Unknown error")
  }

  def broadcast(message: ChatMessage): Unit = {
    participiants.values.foreach(_ ! message)
  }

}


/**
  * Akka-Http runner. Reads Uri and open web socket for each user.
  *
  */
object Main extends App {

    implicit val actorSystem = ActorSystem()
    implicit val actorMaterilizer = ActorMaterializer()


    val route =
      pathPrefix("ws-chat" / Segment) { roomName =>
        parameter("name") { userName =>
          handleWebSocketMessages(Chat.findRoom(roomName).stream(userName))
        }
      }

    val binding = Http().bindAndHandle(route, "localhost", 8080)

}
