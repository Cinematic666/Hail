package com.aistra.hail.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.aistra.hail.enhanced.domain.RuleTrigger

internal object RuleScheduler {
    fun enqueue(context: Context, trigger: RuleTrigger) {
        val eventId = trigger.name + "-" + System.currentTimeMillis() / 1000L
        val request = OneTimeWorkRequestBuilder<RuleEngineWorker>()
            .setInputData(
                workDataOf(
                    RuleEngineWorker.KEY_TRIGGER to trigger.name,
                    RuleEngineWorker.KEY_EVENT_ID to eventId
                )
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "hail-rule-" + eventId,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
