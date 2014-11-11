/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.play.plugins
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.gradle.play.fixtures.PlayCoverage
import org.gradle.test.fixtures.archive.JarTestFixture
import org.junit.Rule

@TargetCoverage({PlayCoverage.DEFAULT})
class PlayApplicationCrossVersionTest extends MultiVersionIntegrationSpec{

    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        buildFile << """
        plugins {
            id 'play-application'
        }

        model {
            components {
                myApp(PlayApplicationSpec){
                    playVersion "${version.playVersion}"
                }
            }
        }

        repositories{
            jcenter()
            maven{
                name = "typesafe-maven-release"
                url = "http://repo.typesafe.com/typesafe/maven-releases"
            }

            dependencies{
                playAppCompile '${version.playDependency}'
            }
        }

        tasks.withType(ScalaCompile) {
            scalaCompileOptions.forkOptions.memoryMaximumSize = '1g'
            scalaCompileOptions.forkOptions.jvmArgs = ['-XX:MaxPermSize=512m']
        }

        tasks.withType(TwirlCompile) {
            forkOptions.memoryInitialSize =  "256m"
            forkOptions.memoryMaximumSize =  "512m"
        }
"""
    }

    def "can build play apps generated by 'play new'"() {
        given:
        resources.maybeCopy("PlayApplicationPluginIntegrationTest/playNew")
        when:
        succeeds("assemble")
        then:
        executedAndNotSkipped(":routesCompileMyAppBinary", ":twirlCompileMyAppBinary", ":createMyAppBinaryJar", ":myAppBinary", ":assemble")

        and:
        jar("build/jars/myApp/myAppBinary.jar").containsDescendants(
                "Routes.class",
                "views/html/index.class",
                "views/html/main.class",
                "controllers/Application.class",
                "images/favicon.png",
                "stylesheets/main.css",
                "javascripts/hello.js",
                "application.conf")

        when:
        succeeds("createMyAppBinaryJar")
        then:
        skipped(":createMyAppBinaryJar", ":twirlCompileMyAppBinary")
    }


    def "can build play certain customized apps"() {
        given:
        resources.maybeCopy("PlayApplicationPluginIntegrationTest/playCustom1")
        when:
        succeeds("assemble")
        then:
        executedAndNotSkipped(":routesCompileMyAppBinary", ":twirlCompileMyAppBinary", ":createMyAppBinaryJar", ":myAppBinary", ":assemble")

        and:
        jar("build/jars/myApp/myAppBinary.jar").containsDescendants(
                "Routes.class",
                "views/html/index.class",
                "views/html/main.class",
                "controllers/Application.class",
                "images/favicon.png",
                "stylesheets/main.css",
                "javascripts/hello.js",
                "application.conf")

        when:
        succeeds("createMyAppBinaryJar")
        then:
        skipped(":createMyAppBinaryJar", ":twirlCompileMyAppBinary")
    }

    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }
}
