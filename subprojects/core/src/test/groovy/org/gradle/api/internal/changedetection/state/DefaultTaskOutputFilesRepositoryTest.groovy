/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.cache.CacheDecorator
import org.gradle.cache.PersistentCache
import org.gradle.internal.hash.TestFileHasher
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.snapshot.WellKnownFileLocations
import org.gradle.internal.snapshot.impl.DefaultFileSystemMirror
import org.gradle.internal.snapshot.impl.DefaultFileSystemSnapshotter
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryIndexedCache
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory(fieldName = "tmpDir")
@UsesNativeServices
class DefaultTaskOutputFilesRepositoryTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def outputFiles = new InMemoryIndexedCache<String, Boolean>(BaseSerializerFactory.BOOLEAN_SERIALIZER)
    def cacheAccess = Stub(PersistentCache) {
        createCache(_) >> outputFiles
    }
    def cacheDecorator = Mock(CacheDecorator)
    def inMemoryCacheDecoratorFactory = Stub(InMemoryCacheDecoratorFactory) {
        decorator(100000, true) >> cacheDecorator
    }
    def repository = new DefaultTaskOutputFilesRepository(cacheAccess, inMemoryCacheDecoratorFactory)
    def snapshotter = new DefaultFileSystemSnapshotter(new TestFileHasher(), new StringInterner(), TestFiles.fileSystem(), new DefaultFileSystemMirror(Stub(WellKnownFileLocations)))

    def "should determine output files generated by Gradle"() {
        def outputFiles = [
            tmpDir.createDir('build/outputs/directory'),
            tmpDir.createFile('build/file'),
            tmpDir.file('build/not-existing'),
        ]

        when:
        repository.recordOutputs(outputFiles.collect { snapshotter.snapshot(it) })

        then:
        repository.isGeneratedByGradle(file('build'))
        repository.isGeneratedByGradle(file('build/outputs'))
        repository.isGeneratedByGradle(file('build/outputs/directory'))
        repository.isGeneratedByGradle(file('build/outputs/directory/subdir'))
        repository.isGeneratedByGradle(file('build/file'))
        repository.isGeneratedByGradle(file('build/file/other'))
        !repository.isGeneratedByGradle(file('build/other'))
        !repository.isGeneratedByGradle(file('build/outputs/other'))
        !repository.isGeneratedByGradle(file('build/not-existing'))
    }

    private File file(String path) {
        tmpDir.file(path).absoluteFile
    }

}
