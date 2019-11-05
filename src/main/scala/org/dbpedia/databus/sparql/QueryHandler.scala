package org.dbpedia.databus.sparql

import java.net.URL

import better.files.File
import org.apache.commons.io.FileUtils
import org.apache.jena.query._
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.{RDFDataMgr, RDFLanguages}
import org.dbpedia.databus.filehandling.FileUtil

object QueryHandler {

  def executeDownloadQuery(queryString: String): Seq[String] = {

    val query: Query = QueryFactory.create(queryString)
    println("\n--------------------------------------------------------\n")
    println(s"""Query:\n\n$query""")

    val qexec: QueryExecution = QueryExecutionFactory.sparqlService("http://databus.dbpedia.org/repo/sparql", query)


    var filesSeq: Seq[String] = Seq[String]()

    try {
      val results: ResultSet = qexec.execSelect
      while (results.hasNext) {
        val resource = results.next().getResource("?file")
        filesSeq = filesSeq :+ resource.toString
      }
    } finally qexec.close()

    filesSeq
  }

  def downloadDataIdFile(url: String, dataIdFile: File):Unit = {
    val queryString =
      s"""PREFIX dataid: <http://dataid.dbpedia.org/ns/core#>
                    PREFIX dcat: <http://www.w3.org/ns/dcat#>
                    SELECT DISTINCT ?dataset WHERE {
                    ?dataset dataid:version ?version .
                    ?dataset dcat:distribution ?distribution .
                    ?distribution dcat:downloadURL <$url> }"""

    val query: Query = QueryFactory.create(queryString)
    val qexec: QueryExecution = QueryExecutionFactory.sparqlService("http://databus.dbpedia.org/repo/sparql", query)

    try {
      val results: ResultSet = qexec.execSelect
      val fileHandler = FileUtil

      if (results.hasNext) {
        val dataidURL = results.next().getResource("?dataset").toString
        //        println(dataidURL)
        FileUtils.copyURLToFile(new URL(dataidURL), dataIdFile.toJava)
      }
    } finally qexec.close()

  }

  def getSHA256Sum(url: String): String = {
    var sha256 = ""
    val queryString =
      s"""PREFIX dataid: <http://dataid.dbpedia.org/ns/core#>
         |PREFIX dcat:   <http://www.w3.org/ns/dcat#>
         |SELECT ?sha256sum  WHERE {
         |  ?s dcat:downloadURL <$url>  .
         |  ?s dataid:sha256sum ?sha256sum .
         |}""".stripMargin

    val query: Query = QueryFactory.create(queryString)
    val qexec: QueryExecution = QueryExecutionFactory.sparqlService("http://databus.dbpedia.org/repo/sparql", query)

    try {
      val results: ResultSet = qexec.execSelect
      val fileHandler = FileUtil

      if (results.hasNext) {
        sha256 = results.next().getLiteral("?sha256sum").toString
      }
    } finally qexec.close()

    sha256
  }

  def executeDataIdQuery(dataIdFile: File): List[String] = {

    val dataidModel: Model = RDFDataMgr.loadModel(dataIdFile.pathAsString, RDFLanguages.NTRIPLES)

    //dir_structure : publisher/group/artifact/version
    var dir_structure = List[String]()

    var query: Query = QueryFactory.create(DataIdQueries.queryGetPublisher())
    var qexec = QueryExecutionFactory.create(query, dataidModel)

    try {
      val results = qexec.execSelect
      if (results.hasNext) {
        //split the URI at the slashes and take the last cell
        val publisher = results.next().getResource("?o").toString.split("/").map(_.trim).last
        dir_structure = dir_structure :+ publisher
      }
    } finally qexec.close()

    query = QueryFactory.create(DataIdQueries.queryGetGroup())
    qexec = QueryExecutionFactory.create(query, dataidModel)

    try {
      val results = qexec.execSelect
      if (results.hasNext) {
        val group = results.next().getResource("?o").toString.split("/").map(_.trim).last
        dir_structure = dir_structure :+ group
      }
    } finally qexec.close()

    query = QueryFactory.create(DataIdQueries.queryGetArtifact())
    qexec = QueryExecutionFactory.create(query, dataidModel)

    try {
      val results = qexec.execSelect
      if (results.hasNext()) {
        val artifact = results.next().getResource("?o").toString().split("/").map(_.trim).last
        dir_structure = dir_structure :+ artifact
      }
    } finally qexec.close()

    query = QueryFactory.create(DataIdQueries.queryGetVersion())
    qexec = QueryExecutionFactory.create(query, dataidModel)

    try {
      val results = qexec.execSelect
      if (results.hasNext()) {
        val version = results.next().getResource("?o").toString().split("/").map(_.trim).last
        dir_structure = dir_structure :+ version
      }
    } finally qexec.close()

    return dir_structure
  }

  def getTypeOfFile(fileURL: String, dataIdFile: File): String = {
    var fileType = ""

    val dataidModel: Model = RDFDataMgr.loadModel(dataIdFile.pathAsString, RDFLanguages.NTRIPLES)

    val query: Query = QueryFactory.create(DataIdQueries.queryGetType(fileURL))
    val qexec = QueryExecutionFactory.create(query, dataidModel)

    try {
      val results = qexec.execSelect
      if (results.hasNext()) {
        fileType = results.next().getLiteral("?type").toString
      }
    } finally qexec.close()

    return fileType
  }

  def getMediatypesOfQuery(list: List[String]) = {
    val files = list.mkString("> , <")
    println(files)
    val queryString =
      s"""PREFIX dataid: <http://dataid.dbpedia.org/ns/core#>
                    PREFIX dcat: <http://www.w3.org/ns/dcat#>
                    SELECT DISTINCT ?type WHERE {
                    ?distribution dcat:mediaType ?type .
                    ?distribution dcat:downloadURL ?du .
                    FILTER (?du in (<$files>))
                    }
                    GROUP BY ?type"""

    val query: Query = QueryFactory.create(queryString)
    val qexec: QueryExecution = QueryExecutionFactory.sparqlService("http://databus.dbpedia.org/repo/sparql", query)


    try {
      val results: ResultSet = qexec.execSelect
      val fileHandler = FileUtil

      while (results.hasNext()) {
        val mediaType = results.next().getResource("?type").toString()
        println(mediaType)
      }
    } finally qexec.close()

  }
}
