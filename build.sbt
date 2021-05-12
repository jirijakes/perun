name := "lnz"

scalaVersion := "3.0.0-RC3"

resolvers += Resolver.publishMavenLocal

libraryDependencies ++= List(
  "dev.zio" %% "zio" % "1.0.7",
  "dev.zio" %% "zio-streams" % "1.0.7",
  "dev.zio" %% "zio-logging" % "0.5.8+12-6ca62f11+20210512-0801-SNAPSHOT",
  // "dev.zio" % "zio-nio" % "1.0.0-RC10",
  "dev.zio" %% "zio-prelude" % "1.0.0-RC4",
  "dev.zio" %% "zio-test" % "1.0.7" % Test,
  "dev.zio" %% "zio-test-sbt" % "1.0.7" % Test,
  //"nl.vroste" %% "rezilience" % "0.6.0+29-1ae49682+20210511-0646-SNAPSHOT",
  // "org.bitcoin-s" % "bitcoin-s-core_2.13" % "0.6.0",
  "org.bitcoin-s" % "bitcoin-s-crypto_2.13" % "0.6.0" excludeAll("org.scodec"),
  "org.bouncycastle" % "bcprov-jdk15on" % "1.68",
  "fr.acinq.secp256k1" % "secp256k1-kmp-jvm" % "0.5.1",
  "fr.acinq.secp256k1" % "secp256k1-kmp-jni-jvm" % "0.5.1",
  "org.scodec" %% "scodec-core" % "2.0.0-RC3",
  "org.scodec" %% "scodec-bits" % "1.1.26",
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

enablePlugins(JavaAppPackaging)
