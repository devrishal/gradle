/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.tooling.next

import org.gradle.integtests.fixtures.BasicGradleDistribution
import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tests.fixtures.ConcurrentTestUtil
import org.gradle.tooling.ProgressListener
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.Project
import org.gradle.tooling.model.idea.IdeaProject
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Issue

@MinToolingApiVersion(currentOnly = true)
@MinTargetGradleVersion(currentOnly = true)
class ConcurrentToolingApiIntegrationTest extends ToolingApiSpecification {

    @Rule def concurrent = new ConcurrentTestUtil()

    def setup() {
        toolingApi.isEmbedded = false
    }

    @Issue("GRADLE-1933")
    def "handles concurrent scenario"() {
        dist.file('build.gradle')  << "apply plugin: 'java'"

        when:
        concurrent.shortTimeout = 30000

        5.times {
            concurrent.start { useToolingApi() }
        }

        then:
        concurrent.finished()
    }

    @Ignore
    //TODO SF enable this test after releasing 1.7
    def "handles concurrent builds with different target Gradle version"() {
        dist.file('build.gradle')  << "apply plugin: 'java'"

        when:
        concurrent.shortTimeout = 30000

        3.times { concurrent.start { useToolingApi() } }
        3.times { concurrent.start { useToolingApi(dist.previousVersion("1.0-milestone-7"))} }

        then:
        concurrent.finished()
    }

    def useToolingApi(BasicGradleDistribution target = null) {
        if (target != null) {
            selectTargetDist(target)
        }

        withConnection { ProjectConnection connection ->
            try {
                def model = connection.getModel(IdeaProject)
                assert model != null
                //a bit more stress:
                connection.newBuild().forTasks('tasks').run()
            } catch (Exception e) {
                throw new RuntimeException("""We might have hit a concurrency problem.
See the full stacktrace and the list of causes to investigate""", e);
            }
        }
    }

    //TODO SF DSLize
    def "receives progress and logging while the model is building"() {
        when:
        int threads = 3

        //create build folders with slightly different builds
        threads.times { idx ->
            dist.file("build$idx/build.gradle") << """
System.out.println 'this is stdout: $idx'
System.err.println 'this is stderr: $idx'
"""
        }

        then:
        threads.times { idx ->
            concurrent.start {
                assertReceivesProgressForModel(idx)
            }
        }

        concurrent.finished()
    }

    private assertReceivesProgressForModel(idx) {
        def stdout = new ByteArrayOutputStream()
        def stderr = new ByteArrayOutputStream()
        def progressMessages = []

        def connector = toolingApi.connector()
        connector.forProjectDirectory(new File(dist.testDir, "build$idx"))
        ProjectConnection connection = connector.connect()
        try {
            def model = connection.model(Project.class)
            model.standardOutput = stdout
            model.standardError = stderr
            model.addProgressListener({ event -> progressMessages << event.description } as ProgressListener)
            assert model.get()
        } finally {
            connection.close();
        }

        assert stdout.toString().contains("this is stdout: $idx")
        assert stderr.toString().contains("this is stderr: $idx")
        assert progressMessages.size() >= 2
        assert progressMessages.pop() == ''
        assert progressMessages.every { it }

        //Below may be very fragile as it depends on progress messages content
        //However, when refactoring the logging code I found ways to break it silently
        //Hence I want to make sure the functionality is not broken. We can remove the assertion later of find better ways of asserting it.
        assert progressMessages == ['Load projects', 'Configure projects', "Resolve dependencies 'classpath'", 'Configure projects', "Resolve dependencies ':classpath'", "Configure projects", "Load projects"]
    }

    def "receives progress and logging while the build is executing"() {
        dist.testFile('build.gradle') << '''
System.out.println 'this is stdout'
System.err.println 'this is stderr'
'''
        def stdout = new ByteArrayOutputStream()
        def stderr = new ByteArrayOutputStream()
        def progressMessages = []
        def events = []

        when:
        withConnection { connection ->
            def build = connection.newBuild()
            build.standardOutput = stdout
            build.standardError = stderr
            build.addProgressListener({ event ->
                progressMessages << event.description
                events << event
            } as ProgressListener)
            build.run()
        }

        then:
        stdout.toString().contains('this is stdout')
        stderr.toString().contains('this is stderr')
        progressMessages.size() >= 2
        progressMessages.pop() == ''
        progressMessages.every { it }

        //Below may be very fragile as it depends on progress messages content
        //However, when refactoring the logging code I found ways to break it silently
        //Hence I want to make sure the functionality is not broken. We can remove the assertion later of find better ways of asserting it.
        progressMessages == ["Execute build", "Configure projects", "Resolve dependencies 'classpath'", "Configure projects", "Resolve dependencies ':classpath'", "Configure projects", "Execute build", "Execute tasks", "Execute :help", "Execute tasks", "Execute build"]
    }
}