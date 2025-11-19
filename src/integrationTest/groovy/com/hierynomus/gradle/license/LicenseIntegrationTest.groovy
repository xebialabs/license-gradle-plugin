/* License added by: GRADLE-LICENSE-PLUGIN
 *
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

package com.hierynomus.gradle.license

import com.google.common.io.Files
import nl.javadude.gradle.plugins.license.header.HeaderDefinitionBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

class LicenseIntegrationTest extends Specification {
    @TempDir
    File projectDir
    
    File buildFile
    File settingsFile
    File license
    
    def setup() {
        buildFile = new File(projectDir, "build.gradle")
        settingsFile = new File(projectDir, "settings.gradle")
        
        settingsFile << """
rootProject.name = 'test-project'
"""
        
        buildFile << """
plugins {
    id "java"
    id "com.xebialabs.license-base"
}

license {
    ignoreFailures = true
}
"""
        license = createLicenseFile()
    }
    
    def runTask(String... tasks) {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(tasks + '--stacktrace')
            .withPluginClasspath()
            .build()
    }
    
    def runTaskWithFailure(String... tasks) {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(tasks + '--stacktrace')
            .withPluginClasspath()
            .buildAndFail()
    }

    def "should work on empty project"() {
        when:
        def result = runTask("licenseMain")

        then:
        result.task(":licenseMain").outcome in [TaskOutcome.SUCCESS, TaskOutcome.NO_SOURCE]
    }

    def "should find single file"() {
        given:
        createPropertiesFile()

        when:
        def result = runTask("licenseMain")

        then:
        result.output.contains("Missing header in:") && result.output.contains("test.properties")
    }

    def "should only find matching extensions"() {
        given:
        createPropertiesFile()
        createTestingFile()

        when:
        def result = runTask("licenseMain")

        then:
        !result.output.contains("prop.testing") || result.output.contains("Unknown file extension:")
        result.output.contains("Unknown file extension:") && result.output.contains("prop.testing")
    }

    def "should be able to add mapping for new extensions"() {
        given:
        createTestingFile()
        buildFile << """
license.mapping {
    testing='SCRIPT_STYLE'
}
"""

        when:
        def result = runTask("licenseMain")

        then:
        result.output.contains("Missing header in:") && result.output.contains("prop.testing")
        !result.output.contains("Unknown file extension:")

    }

    def "should support mapping for files with 'double extension'"() {
        given:
        createFreemarkerShellFile()
        buildFile << """
license.mapping('sh.ftl', 'SCRIPT_STYLE')
"""
        when:
        def result = runTask("licenseMain")

        then:
        result.output.contains("Missing header in:") && result.output.contains("prop.sh.ftl")
        !result.output.contains("Unknown file extension:")
    }

    def "should find multiple files"() {
        given:
        createPropertiesFile()
        createJavaFile()
        createTestingFile()

        when:
        def result = runTask("licenseMain")

        then:
        result.output.contains("Missing header in:") && result.output.contains("test.properties")
        result.output.contains("Missing header in:") && result.output.contains("Test.java")
        result.output.contains("Unknown file extension:") && result.output.contains("prop.testing")

    }

    def "should fail with exception if files are missing headers"() {
        given:
        createPropertiesFile()
        buildFile << "license.ignoreFailures = false\n"

        when:
        def result = runTaskWithFailure("licenseMain")

        then:
        result.task(":licenseMain").outcome == TaskOutcome.FAILED
    }

    def "should not fail on excluded file with missing header (1)"() {
        given:
        createPropertiesFile()
        buildFile << """
license.ignoreFailures = false
license.excludes([\"**/*.properties\"])
"""
        when:
        def result = runTask("licenseMain")

        then:
        result.task(":licenseMain").outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.NO_SOURCE]
    }

    def "should not fail on excluded file with missing header (2)"() {
        given:
        createPropertiesFile()
        buildFile << """
license.ignoreFailures = false
license.exclude \"**/*.properties\"
"""
        when:
        def result = runTask("licenseMain")

        then:
        result.task(":licenseMain").outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.NO_SOURCE]
    }

    def "should not fail on file that does not fit includes pattern (1)"() {
        given:
        createPropertiesFileWithHeader() // Should be included
        createPropertiesFile() // Should be ignored
        buildFile << """
license.ignoreFailures = false
tasks.licenseMain.ext.year = 2012
license.include "**/header.properties"
"""

        when:
        def result = runTask("licenseMain")

        then:
        result.task(":licenseMain").outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.NO_SOURCE]
    }

    def "should not fail on file that does not fit includes pattern (2)"() {
        given:
        createPropertiesFileWithHeader() // Should be included
        createPropertiesFile() // Should be ignored
        buildFile << """
license.ignoreFailures = false
tasks.licenseMain.ext.year = 2012
license.includes(["**/header.properties"])
"""

        when:
        def result = runTask("licenseMain")

        then:
        result.task(":licenseMain").outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.NO_SOURCE]
    }

    def "should correctly mix includes and excludes"() {
        given:
        createPropertiesFileWithHeader() // Should be included
        createPropertiesFile() // Should be ignored (matching include but also exclude)
        createJavaFile() // Should be ignored (not matching include)
        buildFile << """
license.ignoreFailures = false
tasks.licenseMain.ext.year = 2012
license.includes(["**/*.properties"])
license.excludes(["**/test.properties"])
"""

        when:
        def result = runTask("licenseMain")

        then:
        result.task(":licenseMain").outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.NO_SOURCE]
    }

    def "should add header when formatting"() {
        given:
        File propFile = createPropertiesFile()
        def contents = propFile.text

        when:
        def result = runTask("licenseFormatMain")

        then:
        propFile.text == '''#
# This is a sample license created in ${year}
#

key1 = value1
key2 = value2
'''
        propFile.text != contents
    }

    def "should add header to Java file"() {
        given:
        File javaFile = createJavaFile()

        when:
        def result = runTask("licenseFormatMain")

        then:
        javaFile.text == '''/**
 * This is a sample license created in ${year}
 */
public class Test {
        static { System.out.println("Hello") }
}
'''
    }

    def "can apply custom header definition formatting"() {
        given:
        File javaFile = createJavaFile()
        createLicenseFile('''Put a gun against his head,
Pulled my trigger, now he's dead.
Mama, life had just begun,
''')
        buildFile << """
import nl.javadude.gradle.plugins.license.header.*

HeaderDefinitionBuilder customDefinition = HeaderDefinitionBuilder.headerDefinition("bohemian_rhapsody")
    .withFirstLine("Mama, just killed a man,")
    .withEndLine("But now I've gone and thrown it all away.")
    .withBeforeEachLine(" ")
    .withFirstLineDetectionDetectionPattern("Mama")
    .withLastLineDetectionDetectionPattern("away")
tasks.licenseFormatMain.headerDefinitions.add customDefinition
tasks.licenseFormatMain.mapping("java", "bohemian_rhapsody")        
"""

        when:
        def result = runTask("licenseFormatMain")

        then:
        javaFile.text == '''Mama, just killed a man,
 Put a gun against his head,
 Pulled my trigger, now he's dead.
 Mama, life had just begun,
But now I've gone and thrown it all away.
public class Test {
        static { System.out.println("Hello") }
}
'''
    }

    def "should ignore existing header"() {
        given:
        createPropertiesFileWithHeader()
        buildFile << """
tasks.licenseMain.ext.year = 2012
"""

        when:
        def result = runTask("licenseMain")

        then:
        !result.output.contains("Missing header in:") || !result.output.contains("header.properties")

    }

    def "should detect missing header if check=true and skipExistingHeaders=true"() {
        given:
        createPropertiesFile()
        buildFile << """
tasks.licenseMain.ext.year = 2012
tasks.licenseMain {
    check = true
    skipExistingHeaders = true
}
"""
        when:
        def result = runTask("licenseMain")

        then:
        result.output.contains("Missing header in:") && result.output.contains("test.properties")
    }

//    def "should apply license from classpath"() {
//        given:
//        File propFile = createPropertiesFile()
//        buildFile << """
//tasks.licenseFormatMain.headerURI = com.hierynomus.gradle.license.tasks.LicenseCheck.class.getResource("/license/silly.txt").toURI()
//"""
//
//
//        when:
//        def result = runTask("licenseFormatMain")
//
//        then:
//        propFile.text.startsWith("# It's mine, I tell you, mine!")
//    }
//

    File createLicenseFile(String content) {
        File file = new File(projectDir, "LICENSE")
        file.text = content
        file
    }

    File createLicenseFile() {
        createLicenseFile '''This is a sample license created in ${year}'''
    }

    File createJavaFile() {
        File file = new File(projectDir, "src/main/java/Test.java")
        Files.createParentDirs(file)
        file << '''public class Test {
        static { System.out.println("Hello") }
}
'''
        return file
    }

    File createTestingFile() {
        File file = new File(projectDir, "src/main/resources/prop.testing")
        Files.createParentDirs(file)
        file << '''keyA = valueB
keyB = valueB
'''
        return file
    }

    File createFreemarkerShellFile() {
        File file = new File(projectDir, "src/main/resources/prop.sh.ftl")
        Files.createParentDirs(file)
        file << '''#!/bin/bash
echo "Hello world!"
'''
        return file
    }

    File createPropertiesFile() {
        def f = new File(projectDir, "src/main/resources/test.properties")
        Files.createParentDirs(f)
        f << '''key1 = value1
key2 = value2
'''
        return f
    }

    File createPropertiesFileWithHeader() {
        File file = new File(projectDir, "src/main/resources/header.properties")
        Files.createParentDirs(file)
        file << '''# This is a sample license created in 2012
key3 = value3
key4 = value4
'''
        return file
    }

}

