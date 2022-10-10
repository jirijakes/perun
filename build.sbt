name := "lnz"

scalaVersion := "3.2.0"

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

resolvers ++= Resolver.sonatypeOssRepos("snapshots")
resolvers += Resolver.publishMavenLocal
  
val zioVersion = "2.0.2"

ThisBuild / scalafixDependencies ++= List(
  "com.github.liancheng" %% "organize-imports" % "0.5.0",
  "com.github.vovapolu" %% "scaluzzi" % "0.1.18"
)

libraryDependencies ++= List(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-config" % "3.0.2",
  "dev.zio" %% "zio-streams" % zioVersion,
  "dev.zio" %% "zio-logging" % "2.1.2",
  "dev.zio" %% "zio-nio" % "2.0.0",
  "dev.zio" %% "zio-prelude" % "1.0.0-RC16",
  "dev.zio" %% "zio-test" % zioVersion % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
  "dev.zio" %% "zio-test-magnolia" % zioVersion % Test,
  "dev.zio" %% "zio-test-refined" % zioVersion % Test,
  "dev.zio" %% "zio-json" % "0.3.0",
  "nl.vroste" %% "rezilience" % "0.9.0",
  // "org.bitcoin-s" % "bitcoin-s-core_2.13" % "0.6.0",
  "org.bitcoin-s" % "bitcoin-s-core_2.13" % "1.9.6" excludeAll ("org.scodec"),
  "org.bitcoin-s" % "bitcoin-s-crypto_2.13" % "1.9.6" excludeAll ("org.scodec"),
  // "org.bouncycastle" % "bcprov-jdk15on" % "1.68",
  // "fr.acinq.secp256k1" % "secp256k1-kmp-jvm" % "0.5.1",
  // "fr.acinq.secp256k1" % "secp256k1-kmp-jni-jvm" % "0.5.1",
  "com.softwaremill.quicklens" %% "quicklens" % "1.9.0",
  // "org.scodec" %% "scodec-core" % "2.0.0-SNAPSHOT",
  "org.scodec" %% "scodec-bits" % "1.1.34",
  "org.hsqldb" % "hsqldb" % "2.7.0",
  "com.lihaoyi" %% "fansi" % "0.4.0",
  // "com.orientechnologies" % "orientdb-graphdb" % "3.2.0",
  "org.apache.tinkerpop" % "gremlin-core" % "3.6.1",
  "org.apache.tinkerpop" % "tinkergraph-gremlin" % "3.6.1",
  "org.typelevel" %% "paiges-core" % "0.4.2",
  "io.circe" %% "circe-core" % "0.14.3",
  "io.circe" %% "circe-parser" % "0.14.3",
  "net.java.dev.jna" % "jna" % "5.12.1",
  "io.d11" %% "zhttp" % "2.0.0-RC11",
  "com.softwaremill.sttp.client3" %% "zio" % "3.8.2",
  "com.softwaremill.sttp.client3" %% "circe" % "3.8.2",
  "org.zeromq" % "jeromq" % "0.5.2",
  "dev.optics" %% "monocle-core" % "3.1.0",
  "org.typelevel" %% "cats-parse" % "0.3.8",
  "dnsjava" % "dnsjava" % "3.5.1"
  // "org.parboiled" % "parboiled_2.13" % "2.3.0" // not available for Scala3 yet
)

excludeDependencies ++= Seq(
  ExclusionRule("org.scala-lang.modules", "scala-collection-compat_2.13")
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
  // "-Ywarn-unused"
)

scalaModuleInfo ~= (_.map(_.withOverrideScalaVersion(true)))
