package com.hyper.market.installer

import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test

class RootApkInstallerTest {
    @Test
    fun rootInstallUsesOfficialMarketAsInstallerSource() {
        val apk = Files.createTempFile("root-install", ".apk").toFile()
        try {
            val command = RootApkInstaller().command(listOf(apk))
            assertTrue(command.contains("-i ${InstallerIdentity.XIAOMI_MARKET_PACKAGE}"))
        } finally {
            apk.delete()
        }
    }
}
