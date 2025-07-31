package com.gitlab.notscripter.composecli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.gitlab.notscripter.composecli.compose.getApplicationId
import com.gitlab.notscripter.composecli.compose.getMainActivity
import com.gitlab.notscripter.composecli.compose.shln
import java.io.File

class Run : SuspendingCliktCommand() {
    override fun help(context: Context) = "Build and run the app on selected device"

    private val deviceId by option("-d", "--device").required()

    override suspend fun run() {
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
