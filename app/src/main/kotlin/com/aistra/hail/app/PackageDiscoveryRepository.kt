package com.aistra.hail.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

internal data class DiscoveredApp(
    val applicationInfo: ApplicationInfo,
    val packageName: String,
    val userId: Int,
    val installed: Boolean,
    val enabled: Boolean,
    val system: Boolean,
    val hidden: Boolean,
    val suspended: Boolean,
    val stopped: Boolean
)

internal class PackageDiscoveryRepository(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val currentUserId = Process.myUid() / PER_USER_RANGE

    fun queryCurrentUser(): List<DiscoveredApp> {
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or
            PackageManager.MATCH_DISABLED_COMPONENTS
        val applications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(flags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(flags)
        }
        return applications.map { info ->
            val installed = info.flags and ApplicationInfo.FLAG_INSTALLED != 0
            val hidden = runCatching {
                val field = ApplicationInfo::class.java.getDeclaredField("privateFlags")
                field.isAccessible = true
                field.getInt(info) and PRIVATE_FLAG_HIDDEN != 0
            }.getOrDefault(false)
            val suspended = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                info.flags and ApplicationInfo.FLAG_SUSPENDED != 0
            DiscoveredApp(
                applicationInfo = info,
                packageName = info.packageName,
                userId = currentUserId,
                installed = installed,
                enabled = info.enabled,
                system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                hidden = hidden,
                suspended = suspended,
                stopped = info.flags and ApplicationInfo.FLAG_STOPPED != 0
            )
        }.sortedBy { it.packageName.lowercase() }
    }

    private companion object {
        const val PER_USER_RANGE = 100_000
        const val PRIVATE_FLAG_HIDDEN = 1
    }
}
