name := "lnz"

scalaVersion := "3.0.0-RC3"

resolvers += Resolver.publishMavenLocal

libraryDependencies ++= List(
  "dev.zio" % "zio_3.0.0-RC3" % "1.0.7+32-52d1f000-SNAPSHOT",
  "dev.zio" % "zio-streams_3.0.0-RC3" % "1.0.7+32-52d1f000-SNAPSHOT",
  "dev.zio" % "zio-logging_3.0.0-RC3" % "0.5.8+17-a9e86edb-SNAPSHOT",
  // "dev.zio" % "zio-nio" % "1.0.0-RC10",
  "dev.zio" % "zio-prelude_3.0.0-RC3" % "1.0.0-RC4",
  "dev.zio" % "zio-test_3.0.0-RC3" % "1.0.7" % Test,
  "dev.zio" % "zio-test-sbt_3.0.0-RC3" % "1.0.7" % Test,
  //"dev.zio" % "zio-json" % "0.1.4" cross CrossVersion.for3Use2_13,
  //"nl.vroste" %% "rezilience" % "0.6.0+29-1ae49682+20210511-0646-SNAPSHOT",
  // "org.bitcoin-s" % "bitcoin-s-core_2.13" % "0.6.0",
  "org.bitcoin-s" % "bitcoin-s-crypto_2.13" % "0.6.0" excludeAll("org.scodec"),
  "org.bouncycastle" % "bcprov-jdk15on" % "1.68",
  "fr.acinq.secp256k1" % "secp256k1-kmp-jvm" % "0.5.1",
  "fr.acinq.secp256k1" % "secp256k1-kmp-jni-jvm" % "0.5.1",
  "org.scodec" %% "scodec-core" % "2.0.0-RC3",
  "org.scodec" %% "scodec-bits" % "1.1.26",
  "org.hsqldb" % "hsqldb" % "2.6.0"
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

enablePlugins(JavaAppPackaging)
