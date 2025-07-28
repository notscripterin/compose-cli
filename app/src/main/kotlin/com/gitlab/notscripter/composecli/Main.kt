package com.gitlab.notscripter.composecli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.mordant.animation.textAnimation
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File
import java.io.IOException

val t = Terminal()

data class Device(val name: String, val id: String)

data class Template(val name: String, val desc: String, val url: String)

class Compose : SuspendingCliktCommand() {
    override fun help(context: Context) = "A tool for compose"

    override suspend fun run() = Unit
}

class Init : SuspendingCliktCommand() {
    override fun help(context: Context) = "Create new compose project"

    private val name by option().prompt("Enter name for your project")
    private val id by option().prompt("Enter package id (org.example.myapp)")
    private val kmp by option("-kmp", "--kotlin-multi-platform")

    override suspend fun run() {

        // t.println(brightRed("You can use any of the standard ANSI colors"))
        //
        // val style = (bold + black + strikethrough)
        // t.println(cyan("You ${(green on white)("can ${style("nest")} styles")} arbitrarily"))
        //
        // t.println(rgb("#b4eeb4")("You can also use true color and color spaces like HSL"))
        // horizontalLayout {
        //     cell("Spinner:")
        //     cell(Spinner.Dots(initial = 2))
        // }

        val output = sh("ls", "-ls")
        println(output)

        // val templates =
        //     listOf(
        //         Template("compose-template", "Android with compose",
        // "https://gitlab.com/notscripter/compose-template.git"),
        //     )
        //
        // echo("\nAvailable Templates:\n")
        // templates.forEachIndexed { index, t ->
        //     echo("[${index + 1}] ${t.name.padEnd(10)} - ${t.desc}")
        // }
        //
        // echo("\nChoose a template [1-${templates.size}]: ")
        // val choice = readln().toIntOrNull()
        //
        // val selected = templates.getOrNull((choice ?: -1) - 1)
        // if (selected == null) {
        //     echo("❌ Invalid selection. Exiting.")
        //     return
        // }
        //
        // val home = System.getProperty("user.home")
        // val source = File("$home/.namaste/templates/${selected.name}")
        // val target = File("./$name")
        //
        // echo("⏳ Copying ${selected.name} template to ${target.name} ...")
        // // source.copyRecursively(target, overwrite = true)
        // echo("✅ Done! Project created at ./${target.name}")
        // echo(kmp)
    }
}

class Sync : SuspendingCliktCommand() {
    override fun help(context: Context) = "Refresh all dependencies"

    override suspend fun run() {
        sh("./gradlew --refresh-dependencies", "Syncing...")
    }
}

private fun getAdbDevices(): List<Device> {
    val output = sh("adb devices -l", "Finding Devices...")
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

private fun getApplicationId(pwd: String): String {
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

    if (appId.isNullOrBlank()) {
        throw IOException()
    }

    return appId
}

private fun getMainActivity(deviceId: String, applicationId: String): String {
    if (deviceId.isNullOrEmpty() || applicationId.isNullOrEmpty()) {
        throw IOException()
    }

    val mainActivity: String =
        sh("adb -s ${deviceId} shell cmd package resolve-activity --brief ${applicationId}")

    if (mainActivity.isNullOrBlank()) {
        throw IOException()
    }

    return mainActivity.lines().find { it.contains("/") }
}

class Run : SuspendingCliktCommand() {
    override fun help(context: Context) = "Build and run the app on selected device"

    private val deviceId by option("-d", "--device")

    override suspend fun run() {
        val pwd = sh("pwd")
        val appId = getApplicationId(pwd)
        val mainActivity = getMainActivity("bd44c6807d31", appId)
        t.println(mainActivity)
        // sh("./gradlew assembleDebug", "Building...")
        sh(
            "adb -s ${deviceId} install ${pwd}/app/build/outputs/apk/debug/app-debug.apk",
            "Installing...",
        )

        // val devices = getAdbDevices()
        // if (devices.isEmpty()) {
        //     t.println(red("❌No devices found"))
        //     throw IOException()
        // }
        // devices.forEach { t.println("Device: name=${it.name}, id=${it.id}") }
    }
}

suspend fun main(args: Array<String>) = Compose().subcommands(Init(), Sync(), Run()).main(args)

fun sh(command: String, label: String = "Processing"): String {
    val spinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

    val animation =
        t.textAnimation<Int> { frame ->
            val spinner = spinnerFrames[frame % spinnerFrames.size]
            green("$spinner $label")
        }

    t.cursor.hide(showOnExit = true)

    var process: Process? = null
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
