package com.gitlab.notscripter.composecli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.mordant.animation.textAnimation
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.nio.file.Files
import kotlin.collections.emptyList

val t = Terminal()

data class Device(val name: String, val id: String)

data class Template(val name: String, val desc: String, val url: String)

class Compose : SuspendingCliktCommand() {
    override fun help(context: Context) = "A tool for compose"

    override suspend fun run() = Unit
}

private fun matchAndReplace(templateDir: File, replacements: Map<String, String>) {
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

private fun listTemplates(): List<String> {
    val templatesDir = getTemplatesDir()
    return templatesDir.listFiles { file -> file.isDirectory }?.map { it.name } ?: emptyList()
}

private fun getApplicationName(pwd: File): String {
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

private fun getApplicationId(pwd: File): String {
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

private fun getMainActivity(deviceId: String, applicationId: String): String {
    val output =
        sh("adb -s ${deviceId} shell cmd package resolve-activity --brief ${applicationId}")

    var mainActivity = output.lines().find { it.contains("/") }

    if (mainActivity.isNullOrBlank()) {
        throw IOException()
    }

    return mainActivity
}

private fun updateTemplate(
    templateDir: File,
    tempDir: File,
    projectName: String,
    projectId: String,
) {
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

private fun getTemplatesDir(): File {
    val jarPath =
        File(
            URLDecoder.decode(
                object {}.javaClass.protectionDomain.codeSource.location.path,
                "UTF-8",
            )
        )

    return jarPath.parentFile.resolve("templates")
}

private fun getTemplateDir(templateName: String): File {
    val templatesDir = getTemplatesDir()
    return templatesDir.resolve(templateName)
}

class Init : SuspendingCliktCommand() {
    override fun help(context: Context) = "Create new compose project"

    private val pwd = sh("pwd")

    private val projectName by
        option("-n", "--name").prompt("Enter name for the project").help("Project name")

    private val projectId by
        option("-p", "--package").prompt("Enter package id (org.example.myapp)").help("Package Id")

    private val projectPath by
        option("-l", "--location").prompt("Enter where to create the project").help("Project path")
    private val templateName by
        option("-t", "--template").prompt("Enter which template to use").help("Template name")

    override suspend fun run() {
        val templateDir = getTemplateDir(templateName ?: "ComposeTemplate")

        if (!templateDir.exists()) t.println(red("Template not found"))

        val tempDir = Files.createTempDirectory("compose-cli-template").toFile()
        templateDir.copyRecursively(tempDir, overwrite = true)

        updateTemplate(templateDir, tempDir, projectName, projectId)

        val destination = File(projectPath ?: projectName)
        if (destination.exists()) error("Directory already exists")

        tempDir.copyRecursively(destination, overwrite = true)
        t.println(green("✔️ Project '${projectName}' created"))
    }
}

class Sync : SuspendingCliktCommand() {
    override fun help(context: Context) = "Refresh all dependencies"

    override suspend fun run() {
        shln("./gradlew --refresh-dependencies", "Syncing...")
    }
}

class ListTemplates : SuspendingCliktCommand() {
    override fun help(context: Context) = "List Templates"

    override suspend fun run() {
        val templates = listTemplates()

        t.println("Available templates:")
        templates.forEach { t.println(it) }
    }
}

private fun getAdbDevices(): List<Device> {
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

class Run : SuspendingCliktCommand() {
    override fun help(context: Context) = "Build and run the app on selected device"

    private val deviceId by option("-d", "--device").required()

    override suspend fun run() {
        val pwd = sh("pwd")
        val appId = getApplicationId(File(pwd))
        val mainActivity = getMainActivity(deviceId, appId)

        // Build
        shln("./gradlew assembleDebug", "Building...")

        // Install
        shln(
            "adb -s ${deviceId} install ${pwd}/app/build/outputs/apk/debug/app-debug.apk",
            "Installing...",
        )

        // Launch
        shln(
            "adb -s ${deviceId} shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n ${mainActivity}",
            "Launching...",
        )
    }
}

suspend fun main(args: Array<String>) =
    Compose().subcommands(Init(), Sync(), Run(), ListTemplates()).main(args)

private fun sh(command: String): String {
    return ProcessBuilder("sh", "-c", command)
        .redirectErrorStream(true)
        .start()
        .inputStream
        .bufferedReader()
        .readText()
        .trim()
}

private fun shln(command: String, label: String = "Loading..."): String {
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
