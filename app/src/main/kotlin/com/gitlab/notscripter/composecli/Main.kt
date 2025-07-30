package com.gitlab.notscripter.composecli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.animation.textAnimation
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.URLDecoder
import java.nio.file.Files
import java.util.regex.Pattern
import kotlin.collections.emptyList
import kotlin.io.deleteRecursively

val t = Terminal()

data class Device(val name: String, val id: String)

data class Template(val name: String, val desc: String, val url: String)

class Compose : SuspendingCliktCommand() {
    override fun help(context: Context) =
        "Made to ease the development process of Android app with Jetpack-Compose from the terminal"

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

    private val projectName by option("-n", "--name").help("Project name").required()
    private val projectId by option("-p", "--package").help("Package Id").required()
    private val projectPath by option("-l", "--location").help("Project path")
    private val templateName by option("-t", "--template").help("Template name")

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

private fun isValidHexaCode(hex: String): Boolean {
    val hexaPattern = Pattern.compile("^#([a-fA-F0-9]{6}|[a-fA-F0-9]{3})$")

    val matcher = hexaPattern.matcher(hex)

    return matcher.matches()
}

private fun resizeImage(image: File, size: String, output: File) {
    sh("magick ${image} -resize ${size} ${output}")
}

class Launcher : SuspendingCliktCommand() {
    override fun help(context: Context) = "Modify the launcher icon of the app"

    private val foreground by option("-f", "--foreground").required()
    private val background by option("-b", "--background").required()

    private val foregroundImage = File(foreground)
    private var backgroundImage = File(background)

    private class Mipmap(name: String, imageSize: Int, tempDir: File) {
        val imageSize: String = "${imageSize}x${imageSize}"
        val dir: File = File(tempDir, "mipmap-${name}")
    }

    private val tempDir = Files.createTempDirectory("compose-launcher").toFile()
    private val tempResDir = Files.createTempDirectory("compose-launcher-res").toFile()

    private val resDir = File("./app/src/main/res")

    private val mipmaps =
        listOf<Mipmap>(
            Mipmap("xxxhdpi", 432, tempResDir),
            Mipmap("xxhdpi", 324, tempResDir),
            Mipmap("xhdpi", 216, tempResDir),
            Mipmap("hdpi", 162, tempResDir),
            Mipmap("mdpi", 108, tempResDir),
        )

    override suspend fun run() {
        if (!foregroundImage.exists()) error("cant find foreground image")
        if (!backgroundImage.exists()) {
            backgroundImage = File(tempDir, "background.png")
            if (isValidHexaCode(background)) {
                sh("magick -size 1024x1024 xc:${background} ${backgroundImage}")
            } else {
                sh("magick -size 1024x1024 xc:#ffffff ${backgroundImage}")
            }
        }

        if (!resDir.exists()) error("res dir dont exists")
        mipmaps.forEach { it.dir.mkdir() }

        val playStore = Mipmap("play_store", 512, tempResDir.parentFile)
        val anydpiIcLauncher = File(tempResDir, "mipmap-anydpi-v26/ic_launcher.xml")

        // if (!anydpi.exists()) anydpiDir.mkdir()
        if (!anydpiIcLauncher.exists()) {
            anydpiIcLauncher.createNewFile()
            val icLauncher = FileWriter(anydpiIcLauncher)
            icLauncher.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                  <background android:drawable="@mipmap/ic_launcher_background"/>
                  <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
                  <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
                </adaptive-icon>
                """
            )
        }

        if (foregroundImage.exists() && backgroundImage.exists()) {
            val play_store_foreground = File(tempDir, "play_store_foreground.png")
            val play_store_background = File(tempDir, "play_store_background.png")
            sh("magick -resize ${playStore.imageSize} ${foregroundImage} ${play_store_foreground}")
            sh("magick -resize ${playStore.imageSize} ${backgroundImage} ${play_store_background}")
            sh(
                "magick ${play_store_background} ${play_store_foreground} -gravity center -composite ${File(playStore.dir, "play_store_512.png")}"
            )
        }

        mipmaps.forEach {
            sh(
                "magick ${foreground} -resize ${it.imageSize} ${File(it.dir, "ic_launcher_foreground.png")}"
            )
        }
        mipmaps.forEach {
            sh(
                "magick ${background} -resize ${it.imageSize} ${File(it.dir, "ic_launcher_background.png")}"
            )
        }

        // when (background != null) {
        //     isBackgroundColor -> {
        //         mipmaps.forEach {
        //             sh(
        //                 "magick -size ${it.imageSize} xc:${backgroundColor} ${File(it.dir,
        // "ic_launcher_background.png")}"
        //             )
        //         }
        //     }
        //     File(background).exists() -> {
        //         mipmaps.forEach {
        //             sh(
        //                 "magick ${background} -resize ${it.imageSize} ${File(it.dir,
        // "ic_launcher_background.png")}"
        //             )
        //         }
        //     }
        //     else -> error("please provide valid background")
        // }

        t.println(tempResDir)
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
        // val pwd = sh("pwd")
        val appId = getApplicationId(File("./"))
        val mainActivity = getMainActivity(deviceId, appId)

        // Build
        shln("./gradlew assembleDebug", "Building...")

        // Install
        shln(
            "adb -s ${deviceId} install ./app/build/outputs/apk/debug/app-debug.apk",
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
    Compose().subcommands(Init(), Sync(), Run(), ListTemplates(), Launcher()).main(args)

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
