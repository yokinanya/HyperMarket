package com.hyper.market

import android.content.Context
import android.os.Build

internal data class InstallerCapabilities(
    val userActionNotRequiredConfigurable: Boolean,
    val deltaUpdateSupported: Boolean,
    val installationSupported: Boolean,
) {
    companion object {
        fun read(context: Context): InstallerCapabilities {
            val userActionConfigurable = Build.VERSION.SDK_INT >= 31
            val deltaSupported = Build.SUPPORTED_ABIS.none { abi ->
                abi.equals("x86", ignoreCase = true) || abi.equals("x86_64", ignoreCase = true)
            }
            return InstallerCapabilities(
                userActionNotRequiredConfigurable = userActionConfigurable,
                deltaUpdateSupported = deltaSupported,
                installationSupported = true,
            )
        }
    }
}
