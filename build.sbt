name := "lnz"

scalaVersion := "3.1.0-RC2"

addCommandAlias("fmt", "all scalafmtSbt scalafmt Test/scalafmt")
addCommandAlias("fix", "all Compile/scalafix Test/scalafix")
addCommandAlias(
  "fmtCheck",
  "all scalafmtSbtCheck scalafmtCheck Test/scalafmtCheck"
)
addCommandAlias(
  "fixCheck",
  "; Compile/scalafix --check ; Test/scalafix --check "
)
addCommandAlias("prepare", "; fix; fmt")

resolvers ++= List(
  Resolver.publishMavenLocal,
  Resolver.sonatypeRepo("snapshots")
)

val zioVersion = "1.0.12"

ThisBuild / scalafixDependencies ++= List(
  "com.github.liancheng" %% "organize-imports" % "0.5.0",
  "com.github.vovapolu" %% "scaluzzi" % "0.1.18"
)

libraryDependencies ++= List(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-config" % "1.0.10",
  "dev.zio" %% "zio-streams" % zioVersion,
  "dev.zio" %% "zio-logging" % "0.5.12",
//  "dev.zio" % "zio-nio" % "1.0.0-RC10",
  "dev.zio" %% "zio-prelude" % "1.0.0-RC6",
  "dev.zio" %% "zio-test" % zioVersion % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
  "dev.zio" %% "zio-test-magnolia" % zioVersion % Test,
  "dev.zio" %% "zio-test-refined" % zioVersion % Test,
  "dev.zio" %% "zio-json" % "0.2.0-M1+24-eaa49129-SNAPSHOT",
  "nl.vroste" %% "rezilience" % "0.6.2",
  // "org.bitcoin-s" % "bitcoin-s-core_2.13" % "0.6.0",
  "org.bitcoin-s" % "bitcoin-s-core_2.13" % "1.7.0" excludeAll ("org.scodec"),
  "org.bitcoin-s" % "bitcoin-s-crypto_2.13" % "1.7.0" excludeAll ("org.scodec"),
  // "org.bouncycastle" % "bcprov-jdk15on" % "1.68",
  // "fr.acinq.secp256k1" % "secp256k1-kmp-jvm" % "0.5.1",
  // "fr.acinq.secp256k1" % "secp256k1-kmp-jni-jvm" % "0.5.1",
  "com.softwaremill.quicklens" %% "quicklens" % "1.7.4",
  // "org.scodec" %% "scodec-core" % "2.0.0-SNAPSHOT",
  "org.scodec" %% "scodec-bits" % "1.1.28",
  "org.hsqldb" % "hsqldb" % "2.6.0",
  "com.lihaoyi" %% "fansi" % "0.2.14",
  // "com.orientechnologies" % "orientdb-graphdb" % "3.2.0",
  "org.apache.tinkerpop" % "gremlin-core" % "3.5.1",
  "org.apache.tinkerpop" % "tinkergraph-gremlin" % "3.5.1",
  "org.typelevel" %% "paiges-core" % "0.4.2",
  "io.circe" %% "circe-core" % "0.14.1",
  "io.circe" %% "circe-parser" % "0.14.1",
  "net.java.dev.jna" % "jna" % "5.9.0",
  "io.d11" %% "zhttp" % "1.0.0.0-RC17",
  "com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.3.14",
  "com.softwaremill.sttp.client3" %% "circe" % "3.3.14",
  "org.zeromq" % "jeromq" % "0.5.2",
  "dev.optics" %% "monocle-core" % "3.1.0",
  "org.typelevel" %% "cats-parse" % "0.3.4",
  "dnsjava" % "dnsjava" % "3.4.2"
  // "org.parboiled" % "parboiled_2.13" % "2.3.0" // not available for Scala3 yet
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

enablePlugins(JavaAppPackaging)

// Pass SIGINT into running app to let it gracefully shutdown
Global / cancelable := false

semanticdbEnabled := true

Compile / doc / scalacOptions ++= Seq("-snippet-compiler:compile")

scalacOptions ++= List(
  // "-explain",
  "-new-syntax",
  "-Ywarn-unused"
)

scalaModuleInfo ~= (_.map(_.withOverrideScalaVersion(true)))
