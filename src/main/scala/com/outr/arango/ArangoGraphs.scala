package com.outr.arango

import com.outr.arango.rest._
import io.circe.{Decoder, Encoder}
import io.youi.http.Method
import io.circe.generic.auto._
import io.circe.generic.semiauto._

import scala.concurrent.Future

class ArangoGraphs(db: ArangoDB) {
  def list(): Future[GraphList] = {
    db.call[GraphList]("gharial", Method.Get)
  }

  def apply(name: String): ArangoGraph = new ArangoGraph(name, db)
}

class ArangoGraph(val name: String, val db: ArangoDB) {
  def create(orphanCollections: List[String] = Nil,
             edgeDefinitions: List[EdgeDefinition] = Nil,
             isSmart: Option[Boolean] = None,
             smartGraphAttribute: Option[String] = None,
             numberOfShards: Option[Int] = None): Future[GraphResponse] = {
    val options = if (smartGraphAttribute.nonEmpty || numberOfShards.nonEmpty) {
      Some(GraphOptions(smartGraphAttribute, numberOfShards))
    } else {
      None
    }
    val request = CreateGraphRequest(name, orphanCollections, edgeDefinitions, isSmart, options)
    db.restful[CreateGraphRequest, GraphResponse]("gharial", request)
  }

  def get(): Future[GraphResponse] = {
    db.call[GraphResponse](s"gharial/$name", Method.Get)
  }

  def listVertex(): Future[GraphCollectionList] = {
    db.call[GraphCollectionList](s"gharial/$name/vertex", Method.Get)
  }

  def listEdge(): Future[GraphCollectionList] = {
    db.call[GraphCollectionList](s"gharial/$name/edge", Method.Get)
  }

  def vertex(name: String): ArangoVertex = new ArangoVertex(name, this)

  def edge(name: String): ArangoEdge = new ArangoEdge(name, this)

  def delete(dropCollections: Boolean): Future[DeleteResponse] = {
    db.call[DeleteResponse](s"gharial/$name", Method.Delete, Map("dropCollections" -> dropCollections.toString))
  }
}

class ArangoVertex(val name: String, graph: ArangoGraph) {
  def create(): Future[GraphResponse] = {
    graph.db.restful[AddVertexRequest, GraphResponse](s"gharial/${graph.name}/vertex", AddVertexRequest(name))
  }

  def insert[T](document: T, waitForSync: Option[Boolean] = None)(implicit encoder: Encoder[T]): Future[VertexInsert] = {
    graph.db.restful[T, VertexInsert](s"gharial/${graph.name}/vertex/$name", document, params = waitForSync.map(b => Map("waitForSync" -> b.toString)).getOrElse(Map.empty))
  }

  def apply[T](key: String)(implicit encoder: Encoder[T], decoder: Decoder[T]): Future[VertexResult[T]] = {
    graph.db.call[VertexResult[T]](s"gharial/${graph.name}/vertex/$name/$key", Method.Get)
  }

  def modify[T](key: String, value: T)(implicit encoder: Encoder[T], decoder: Decoder[T]): Future[VertexResult[T]] = {
    graph.db.restful[T, VertexResult[T]](s"gharial/${graph.name}/vertex/$name/$key", value, method = Method.Patch)
  }

  def replace[T](key: String, value: T)(implicit encoder: Encoder[T], decoder: Decoder[T]): Future[VertexResult[T]] = {
    graph.db.restful[T, VertexResult[T]](s"gharial/${graph.name}/vertex/$name/$key", value, method = Method.Put)
  }

  def delete(key: String): Future[DeleteResponse] = {
    graph.db.call[DeleteResponse](s"gharial/${graph.name}/vertex/$name/$key", Method.Delete)
  }

  def delete(): Future[GraphResponse] = {
    graph.db.call[GraphResponse](s"gharial/${graph.name}/vertex/$name", Method.Delete)
  }
}

class ArangoEdge(val name: String, graph: ArangoGraph) {
  def create(from: List[String], to: List[String]): Future[GraphResponse] = {
    graph.db.restful[EdgeDefinition, GraphResponse](s"gharial/${graph.name}/edge", EdgeDefinition(name, from, to))
  }

  def create(from: String, to: String): Future[GraphResponse] = create(List(from), List(to))

  def create(from: ArangoVertex, to: ArangoVertex): Future[GraphResponse] = create(from.name, to.name)

  def insert[T <: Edge](edge: T)(implicit encoder: Encoder[T]): Future[EdgeResult] = {
    graph.db.restful[T, EdgeResult](s"gharial/${graph.name}/edge/$name", edge)
  }

  def replace(from: List[String], to: List[String]): Future[GraphResponse] = {
    // TODO: remove these once Circe fixes named-arg problem (method = Method.Put causes this)
    implicit val edgeDefinitionEncoder = deriveEncoder[EdgeDefinition]
    implicit val graphResponseDecoder = deriveDecoder[GraphResponse]

    graph.db.restful[EdgeDefinition, GraphResponse](s"gharial/${graph.name}/edge/$name", EdgeDefinition(name, from, to), method = Method.Put)
  }

  def delete(dropCollection: Boolean = true): Future[GraphResponse] = {
    graph.db.call[GraphResponse](s"gharial/${graph.name}/edge/$name", Method.Delete, Map(
      "dropCollection" -> dropCollection.toString)
    )
  }
}