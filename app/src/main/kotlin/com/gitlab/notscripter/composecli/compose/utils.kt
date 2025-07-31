package com.gitlab.notscripter.composecli.compose

import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.animation.textAnimation
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.regex.Pattern
import kotlin.collections.emptyList
import kotlin.io.deleteRecursively

data class Device(val name: String, val id: String)

data class Template(val name: String, val desc: String, val url: String)

val t: Terminal = Terminal()

fun sh(command: String): String {
    return ProcessBuilder("sh", "-c", command)
        .redirectErrorStream(true)
        .start()
        .inputStream
        .bufferedReader()
        .readText()
        .trim()
}

fun shln(command: String, label: String = "Loading..."): String {
    var process: Process? = null

    val spinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

    val animation =
        t.textAnimation<Int> { frame ->
            val spinner = spinnerFrames[frame % spinnerFrames.size]
            green("$spinner $label")
        }

    t.cursor.hide(showOnExit = true)

    try {
        process = ProcessBuilder("sh", "-c", command).start()

        var frame = 0
        while (process.isAlive) {
            animation.update(frame++)
            Thread.sleep(100)
        }
        val output = process.inputStream.bufferedReader().readText()

        if (process.exitValue() != 0) {
            // val errorOutput = process.errorStream.bufferedReader().readText()
            // throw IOException("❌Failed to execute command: ${command}")
            // t.println(red("❌Failed to execute command: ${command}"))
            throw IOException()
        }

        animation.clear()
        t.println(green("✔️ $label"))

        return output.trim()
    } catch (e: IOException) {
        animation.clear()
        t.println(red("❌$label"))

        // throw IOException(e.message)
        t.println(red("❌Failed to execute command: ${command}"))
        throw IOException()
    } finally {
        animation.stop()
        process?.destroy()
        t.cursor.show()
    }
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

fun listTemplates(): List<String> {
    val templatesDir = getTemplatesDir()
    return templatesDir.listFiles { file -> file.isDirectory }?.map { it.name } ?: emptyList()
}

fun getApplicationName(pwd: File): String {
    val file = File("${pwd}/settings.gradle.kts")
    if (!file.exists()) {
        throw IOException()
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
        throw IOException()
    }

    return appName
}

fun getApplicationId(pwd: File): String {
    val file = File("${pwd}/app/build.gradle.kts")
    if (!file.exists()) {
        throw IOException()
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
        throw IOException()
    }

    return appId
}

fun getMainActivity(deviceId: String, applicationId: String): String {
    val output =
        sh("adb -s ${deviceId} shell cmd package resolve-activity --brief ${applicationId}")

    var mainActivity = output.lines().find { it.contains("/") }

    if (mainActivity.isNullOrBlank()) {
        throw IOException()
    }

    return mainActivity
}

fun updateTemplate(templateDir: File, tempDir: File, projectName: String, projectId: String) {
    val templateAppName = getApplicationName(templateDir)
    val templateAppId = getApplicationId(templateDir)

    val topLevelPath = templateAppId.substringBefore(".")

    matchAndReplace(tempDir, mapOf(templateAppId to projectId, templateAppName to projectName))

    val templatePackagePath = templateAppId.replace(".", File.separator)
    val tempPackagePath = projectId.replace(".", File.separator)

    val rootDir = File("${tempDir}/app/src/main/java")
    val topLevelTempDir = File(rootDir, "temp")

    File(rootDir, topLevelPath).renameTo(topLevelTempDir)

    val templatePackageDir = File(topLevelTempDir, templatePackagePath.substringAfter("/"))
    val tempPackageDir = File(rootDir, tempPackagePath)

    tempPackageDir.mkdirs()
    templatePackageDir.copyRecursively(tempPackageDir, overwrite = true)

    topLevelTempDir.deleteRecursively()
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

fun getTemplateDir(templateName: String): File {
    val templatesDir = getTemplatesDir()
    return templatesDir.resolve(templateName)
}

fun getAdbDevices(): List<Device> {
    val output = sh("adb devices -l")
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

    // if (devices.isEmpty()) t.println("No devices found")
    // else devices.forEach { t.println("Device: name=${it.name}, id=${it.id}") }
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
