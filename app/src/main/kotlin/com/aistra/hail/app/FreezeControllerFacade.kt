package com.aistra.hail.app

import com.aistra.hail.BuildConfig
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.enhanced.domain.FreezeAction
import com.aistra.hail.enhanced.domain.FreezeController
import com.aistra.hail.enhanced.domain.FreezeOperationResult

internal object FreezeControllerFacade {
    fun execute(packageNames: Collection<String>, frozen: Boolean): List<FreezeOperationResult> {
        val mode = HailData.workingMode
        val backend = ModeFreezeBackend(mode)
        val controller = FreezeController(
            backends = listOf(backend),
            journal = SharedPreferencesFreezeJournal(app)
        )
        return controller.execute(
            packageNames = packageNames,
            action = if (frozen) FreezeAction.FREEZE else FreezeAction.UNFREEZE,
            backendId = mode,
            userId = android.os.Process.myUid() / 100_000,
            protectedPackages = setOf(BuildConfig.APPLICATION_ID)
        )
    }

    fun recoverPending() {
        val mode = HailData.workingMode
        val controller = FreezeController(
            backends = listOf(ModeFreezeBackend(mode)),
            journal = SharedPreferencesFreezeJournal(app)
        )
        controller.recoverPending()
    }
}
