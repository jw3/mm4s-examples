logLevel := Level.Warn

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.0")
addSbtPlugin("com.typesafe.sbt" %% "sbt-native-packager" % "1.0.6")
addSbtPlugin("com.updateimpact" % "updateimpact-sbt-plugin" % "2.1.1")
