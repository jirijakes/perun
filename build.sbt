name := "lnz"

scalaVersion := "3.0.0"

resolvers += Resolver.publishMavenLocal

val zioVersion = "1.0.8" //+51-b1a3621b-SNAPSHOT"

ThisBuild / scalafixDependencies ++= List(
  "com.github.liancheng" %% "organize-imports" % "0.5.0",
  "com.github.vovapolu"  %% "scaluzzi"         % "0.1.18"
)

libraryDependencies ++= List(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-streams" % zioVersion,
//  "dev.zio" % "zio-logging_3.0.0-RC3" % "0.5.8",
  // "dev.zio" % "zio-nio" % "1.0.0-RC10",
  "dev.zio" %% "zio-prelude" % "1.0.0-RC5",
  "dev.zio" %% "zio-test" % zioVersion % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
  "dev.zio" %% "zio-test-magnolia" % zioVersion % Test,
  "dev.zio" %% "zio-test-refined" % zioVersion % Test,
  // "dev.zio" % "zio-json" % "0.1.4",
  //"nl.vroste" %% "rezilience" % "0.6.0+29-1ae49682+20210511-0646-SNAPSHOT",
  // "org.bitcoin-s" % "bitcoin-s-core_2.13" % "0.6.0",
  "org.bitcoin-s" % "bitcoin-s-core_2.13" % "0.6.0" excludeAll("org.scodec"),
  "org.bitcoin-s" % "bitcoin-s-crypto_2.13" % "0.6.0" excludeAll("org.scodec"),
  // "org.bouncycastle" % "bcprov-jdk15on" % "1.68",
  // "fr.acinq.secp256k1" % "secp256k1-kmp-jvm" % "0.5.1",
  // "fr.acinq.secp256k1" % "secp256k1-kmp-jni-jvm" % "0.5.1",
  "com.softwaremill.quicklens" %% "quicklens" % "1.7.3",
  "org.scodec" %% "scodec-core" % "2.0.0",
  "org.scodec" %% "scodec-bits" % "1.1.27",
  "org.hsqldb" % "hsqldb" % "2.6.0",
  "com.lihaoyi" %% "fansi" % "0.2.14",
  // "com.orientechnologies" % "orientdb-graphdb" % "3.2.0",
  "org.apache.tinkerpop" % "gremlin-core" % "3.5.0",
  "org.apache.tinkerpop" % "tinkergraph-gremlin" % "3.5.0",
  "org.typelevel" % "paiges-core_2.13" % "0.4.1",
  "io.circe" %% "circe-core" % "0.14.1",
  "io.circe" %% "circe-parser" % "0.14.1",
  "net.java.dev.jna" % "jna" % "5.8.0",
  // "io.d11" %% "zhttp" % "1.0.0.0-RC16+18-bfbb9858+20210530-1124-SNAPSHOT",
  "com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.3.5",
  "com.softwaremill.sttp.client3" %% "circe" % "3.3.5",
  "org.zeromq" % "jeromq" % "0.5.2"
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

enablePlugins(JavaAppPackaging)

// Pass SIGINT into running app to let it gracefully shutdown
Global / cancelable := false

semanticdbEnabled := true

scalacOptions ++= List("-explain", "-new-syntax", "-Xlint:unused", "-Wunused:imports")

scalaModuleInfo ~= (_.map(_.withOverrideScalaVersion(true)))
