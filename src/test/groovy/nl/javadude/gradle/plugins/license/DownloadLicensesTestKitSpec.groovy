/*
 * Copyright (C)2011 - Jeroen van Erp <jeroen@javadude.nl>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.javadude.gradle.plugins.license

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

class DownloadLicensesTestKitSpec extends Specification {
    @TempDir
    File projectDir
    
    File buildFile
    File settingsFile
    
    def setup() {
        buildFile = new File(projectDir, "build.gradle")
        settingsFile = new File(projectDir, "settings.gradle")
        
        settingsFile << """
rootProject.name = 'test-project'
"""
    }
    
    def runTask(String... tasks) {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(tasks + '--stacktrace')
            .withPluginClasspath()
            .build()
    }
    
    def "Should correctly take project.buildDir into account for generated reports"() {
        given:
        buildFile << """
plugins {
    id 'java'
    id 'com.xebialabs.license'
}

buildDir = "generated"

dependencies {
    implementation 'com.google.guava:guava:14.0'
}

repositories { mavenCentral() }
"""
        when:
        def result = runTask('downloadLic')

        then:
        new File(projectDir, "generated/reports").exists()
        !new File(projectDir, "build").exists()
    }
}
