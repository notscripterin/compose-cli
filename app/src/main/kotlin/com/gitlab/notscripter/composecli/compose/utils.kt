package com.gitlab.notscripter.composecli.compose

import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.animation.textAnimation
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import com.gitlab.notscripter.composecli.model.*
import java.io.File
import java.net.URLDecoder
import java.util.regex.Pattern
import kotlin.io.deleteRecursively

val t: Terminal = Terminal()

fun sh(command: String, label: String? = null, printOutput: Boolean = false): String? {
    var process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
    t.cursor.hide(showOnExit = true)

    /*
    Runtime.getRuntime().addShutdownHook(Thread { if (process.isAlive) process.destroyForcibly() })
    */

    if (!label.isNullOrEmpty()) {
        val spinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

        val animation =
            t.textAnimation<Int> { frame ->
                val spinner = spinnerFrames[frame % spinnerFrames.size]
                green("$spinner $label...")
            }

        var frame = 0
        while (process.isAlive) {
            animation.update(frame++)
            Thread.sleep(100)
        }

        if (process.exitValue() != 0) {
            animation.clear()
            t.println(red("❌$label..."))
            t.println(red("❌Failed to execute command: ${command}"))
            return null
        }

        animation.clear()
        t.println(green("✔️ $label..."))

        animation.stop()
    } else if (printOutput == true) {
        process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                when {
                    line.contains(" D ") -> t.println(blue(line))
                    line.contains(" I ") -> t.println(green(line))
                    line.contains(" W ") -> t.println(yellow(line))
                    line.contains(" E ") -> t.println(red(line))
                    line.contains(" F ") -> t.println(red(line))
                }
            }
        }
        process.waitFor()
    }

    val output = process.inputStream.bufferedReader().readText().trim()
    process.destroy()
    t.cursor.show()
    return output
}

fun matchAndReplace(templateDir: File, replacements: Map<String, String>) {
    templateDir
        .walkTopDown()
        .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }
        .forEach { file ->
            val content = file.readText()
            var modified = content

            replacements.forEach { (key, value) -> modified = modified.replace(key, value) }

            if (content != modified) {
                file.writeText(modified)
            }
        }
}

fun getTemplatesDir(): File {
    val jarPath =
        File(
            URLDecoder.decode(
                object {}.javaClass.protectionDomain.codeSource.location.path,
                "UTF-8",
            )
        )

    return jarPath.parentFile.resolve("templates")
}

fun getTemplateDir(templateName: String): File? {
    val templatesDir = getTemplatesDir()
    val templatePath = templatesDir.resolve(templateName)
    if (!templatePath.exists()) {
        t.println(red("'${templatesDir}/${templateName}' not found."))
        return null
    }

    return templatePath
}

fun getAdbDevices(): List<Device>? {
    val output = sh("adb devices -l")
    if (output == null) return null

    val devices: List<Device> =
        output
            .split("\n")
            .subList(1, output.split("\n").size)
            .filter { it.isNotBlank() && it.contains("device ") }
            .mapNotNull { line ->
                val parts = line.split("\\s+".toRegex(), limit = 2)
                if (parts.size < 2) null
                else
                    Device(
                        name =
                            parts[1].substringAfter("model:").substringBefore(" ").takeIf {
                                it.isNotEmpty()
                            } ?: "Unknown",
                        id = parts[0].trim(),
                    )
            }

    if (devices.isEmpty()) {
        t.println(red("adb devices not found."))
        return null
    }
    return devices
}

fun isValidHexaCode(hex: String): Boolean {
    val hexaPattern = Pattern.compile("^#([a-fA-F0-9]{6}|[a-fA-F0-9]{3})$")

    val matcher = hexaPattern.matcher(hex)

    return matcher.matches()
}

fun resizeImage(image: File, size: String, output: File) {
    sh("magick ${image} -resize ${size} ${output}")
}

fun listTemplates(): List<String>? {
    val templatesDir = getTemplatesDir()
    val templates = templatesDir.listFiles { file -> file.isDirectory }?.map { it.name }
    if (templates.isNullOrEmpty()) {
        t.println("No template found.")
        return null
    }
    return templates
}

fun getApplicationName(pwd: File): String? {
    val file = File("${pwd}/settings.gradle.kts")
    if (!file.exists()) {
        t.println(red("'${pwd}/settings.gradle.kts' not found"))
        return null
    }
    val appName =
        file
            .readLines()
            .map { it.trim() }
            .find { it.startsWith("rootProject.name") }
            ?.substringAfter("=")
            ?.trim()
            ?.removeSurrounding("\"")

    if (appName.isNullOrBlank()) {
        t.println(red("'rootProject.name' not found in '${file}'"))
        return null
    }

    return appName
}

fun getApplicationId(pwd: File): String? {
    val file = File("${pwd}/app/build.gradle.kts")
    if (!file.exists()) {
        t.println(red("'${file}' not found"))
        return null
    }
    val appId =
        file
            .readLines()
            .map { it.trim() }
            .find { it.startsWith("applicationId") }
            ?.substringAfter("=")
            ?.trim()
            ?.removeSurrounding("\"")

    if (appId.isNullOrBlank()) {
        t.println(red("'applicationId' not found in '${file}'"))
        return null
    }

    return appId
}

fun getMainActivity(deviceId: String, applicationId: String): String? {
    val output =
        sh("adb -s ${deviceId} shell cmd package resolve-activity --brief ${applicationId}")
    if (output == null) return null

    var mainActivity = output.lines().find { it.contains("/") }

    if (mainActivity.isNullOrBlank()) {
        t.println(red("'MainActivity' not found."))
        return null
    }

    return mainActivity
}

fun updateTemplate(
    templateDir: File,
    tempDir: File,
    projectName: String,
    projectId: String,
): Boolean {
    val templateAppName = getApplicationName(templateDir)
    val templateAppId = getApplicationId(templateDir)

    if (templateAppId == null || templateAppName == null) return false

    val topLevelPath = templateAppId.substringBefore(".")

    matchAndReplace(tempDir, mapOf(templateAppId to projectId, templateAppName to projectName))

    val packagePath = projectId.replace(".", File.separator)
    val templatePackagePath = templateAppId.replace(".", File.separator)

    val mainJavaDir = File("${tempDir}/app/src/main/java")
    val testJavaDir = File("${tempDir}/app/src/test/java")
    val androidTestJavaDir = File("${tempDir}/app/src/androidTest/java")

    val tempMainJavaDir = File(mainJavaDir, "temp")
    val tempTestJavaDir = File(testJavaDir, "temp")
    val tempAndroidTestJavaDir = File(androidTestJavaDir, "temp")

    val templateMainPackageDir = File(tempMainJavaDir, templatePackagePath.substringAfter("/"))
    val templateTestPackageDir = File(tempTestJavaDir, templatePackagePath.substringAfter("/"))
    val templateAndroidTestPackageDir =
        File(tempAndroidTestJavaDir, templatePackagePath.substringAfter("/"))

    val tempPackageDir = File(mainJavaDir, packagePath)
    val tempTestDir = File(testJavaDir, packagePath)
    val tempAndroidTestDir = File(androidTestJavaDir, packagePath)

    File(mainJavaDir, topLevelPath).renameTo(tempMainJavaDir)
    File(testJavaDir, topLevelPath).renameTo(tempTestJavaDir)
    File(androidTestJavaDir, topLevelPath).renameTo(tempAndroidTestJavaDir)

    tempPackageDir.mkdirs()
    tempTestDir.mkdirs()
    tempAndroidTestDir.mkdirs()

    templateMainPackageDir.copyRecursively(tempPackageDir, overwrite = true)
    templateTestPackageDir.copyRecursively(tempTestDir, overwrite = true)
    templateAndroidTestPackageDir.copyRecursively(tempAndroidTestDir, overwrite = true)

    tempMainJavaDir.deleteRecursively()
    tempTestJavaDir.deleteRecursively()
    tempAndroidTestJavaDir.deleteRecursively()

    return true
}
