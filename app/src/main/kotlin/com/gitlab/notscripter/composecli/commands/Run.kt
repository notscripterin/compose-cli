package com.gitlab.notscripter.composecli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.gitlab.notscripter.composecli.compose.getAdbDevices
import com.gitlab.notscripter.composecli.compose.getApplicationId
import com.gitlab.notscripter.composecli.compose.getMainActivity
import com.gitlab.notscripter.composecli.compose.shln
import com.gitlab.notscripter.composecli.model.Device
import java.io.File

class Run : SuspendingCliktCommand() {
    override fun help(context: Context) =
        "   Build and launch on a device or emulator — no mouse needed"

    private val deviceId by
        option("-d", "--device").help("ADB device ID (use `adb devices` to list)")

    override suspend fun run() {
        var selectedDeviceId = deviceId ?: ""
        if (deviceId.isNullOrEmpty()) {
            val adbDevices: List<Device> = getAdbDevices()
            when (adbDevices.size) {
                1 -> selectedDeviceId = adbDevices[0].id
                in 2..10 ->
                    error(
                        "There is more than one adb devices, please specify one deviceId with '-d' or '--devide' option."
                    )
                else -> error("There is no adb devices found.")
            }
        }

        val appId = getApplicationId(File("./"))
        val mainActivity = getMainActivity(selectedDeviceId, appId)

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
