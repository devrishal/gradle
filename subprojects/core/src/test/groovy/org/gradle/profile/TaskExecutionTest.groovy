/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.profile

import org.gradle.api.Task
import org.gradle.api.tasks.TaskState
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 3/19/13
 */
class TaskExecutionTest extends Specification {

    def "knows task status"() {
        def skipped = Stub(TaskState) {
            getSkipped() >> true
            getSkipMessage() >> "Skipped for a good reason."
        }
        def busy = Stub(TaskState) {
            getSkipped() >> false
            getDidWork() >> true
        }
        def noWork = Stub(TaskState) {
            getSkipped() >> false
            getDidWork() >> false
        }

        expect:
        new TaskExecution(Stub(Task)).completed(skipped).status == "Skipped for a good reason."
        new TaskExecution(Stub(Task)).completed(busy).status == ""
        new TaskExecution(Stub(Task)).completed(noWork).status == TaskExecution.NO_WORK_MESSAGE
    }
}
