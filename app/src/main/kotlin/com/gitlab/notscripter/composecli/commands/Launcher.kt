package com.gitlab.notscripter.composecli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.gitlab.notscripter.composecli.compose.isValidHexaCode
import com.gitlab.notscripter.composecli.compose.sh
import com.gitlab.notscripter.composecli.compose.t
import java.io.File
import java.io.FileWriter
import java.nio.file.Files

class Launcher : SuspendingCliktCommand() {
    override fun help(context: Context) =
        "🎨Generate adaptive launcher icons without dragging SVGs around"

    private val foregroundOpt by
        option("-f", "--foreground").help("Image path for the foreground layer").prompt()
    private val backgroundOpt by
        option("-b", "--background").help("Image path or HEX color for the background").prompt()

    private val foregroundArg by argument().optional()
    private val backgroundArg by argument().optional()

    private class Mipmap(name: String, imageSize: Int, tempDir: File) {

        val imageSize: String = "${imageSize}x${imageSize}"
        val dir: File = File(tempDir, "mipmap-${name}")
    }

    private val tempDir = Files.createTempDirectory("compose-launcher").toFile()
    // private val tempResDir = Files.createTempDirectory("compose-launcher-res").toFile()
    private val tempResDir = File(tempDir, "res")
    private val tempDirToBeDeleted = File(tempDir, ".temp")

    private val mainDir = File("./app/src/main")
    private val resDir = File(mainDir, "res")

    private val playStore = Mipmap("play_store", 512, tempDir)
    private val mipmaps =
        listOf<Mipmap>(
            Mipmap("xxxhdpi", 432, tempResDir),
            Mipmap("xxhdpi", 324, tempResDir),
            Mipmap("xhdpi", 216, tempResDir),
            Mipmap("hdpi", 162, tempResDir),
            Mipmap("mdpi", 108, tempResDir),
        )

    override suspend fun run() {
        val foreground = foregroundOpt ?: foregroundArg
        val background = backgroundOpt ?: backgroundArg

        if (foreground == null) {
            error("foreground is required")
        } else if (background == null) {
            error("background is required")
        }

        val foregroundImage = File(foreground)
        var backgroundImage = File(background)

        tempResDir.mkdir()
        tempDirToBeDeleted.mkdir()

        if (!foregroundImage.exists()) error("cant find foreground image")
        if (!backgroundImage.exists()) {
            backgroundImage = File(tempDirToBeDeleted, "background.png")
            if (isValidHexaCode(background)) {
                sh("magick -size 1024x1024 xc:${background} ${backgroundImage}")
            } else {
                sh("magick -size 1024x1024 xc:#ffffff ${backgroundImage}")
            }
        }

        if (!resDir.exists()) error("res dir dont exists")

        mipmaps.forEach { it.dir.mkdir() }

        val anydpiDir = File(resDir, "mipmap-anydpi-v26")
        val anydpiIcLauncher = File(anydpiDir, "ic_launcher.xml")
        val anydpiTempDir = File(tempResDir, "mipmap-anydpi-v26")
        val anydpiTempIcLauncher = File(anydpiDir, "ic_launcher.xml")

        if (!anydpiDir.exists()) anydpiTempDir.mkdir()

        if (!anydpiIcLauncher.exists()) {
            anydpiTempIcLauncher.createNewFile()
            val icLauncher = FileWriter(anydpiTempIcLauncher)
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

        // Foreground
        mipmaps.forEach {
            sh(
                "magick ${foregroundImage} -resize ${it.imageSize} ${File(it.dir, "ic_launcher_foreground.png")}"
            )
        }
        // Background
        mipmaps.forEach {
            sh(
                "magick ${backgroundImage} -resize ${it.imageSize} ${File(it.dir, "ic_launcher_background.png")}"
            )
        }
        // Merged
        mipmaps.forEach {
            sh(
                "magick ${File(it.dir, "ic_launcher_background.png")} ${File(it.dir, "ic_launcher_foreground.png")} -gravity center -composite ${File(it.dir, "ic_launcher.png")}"
            )
        }

        tempDirToBeDeleted.delete()
        tempDir.copyRecursively(mainDir, overwrite = true)
        tempDir.delete()
        t.println(tempDir)
    }
}
