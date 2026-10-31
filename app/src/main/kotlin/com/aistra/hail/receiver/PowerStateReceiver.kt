package com.aistra.hail.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aistra.hail.enhanced.domain.RuleTrigger
import com.aistra.hail.work.RuleScheduler

class PowerStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> RuleScheduler.enqueue(context, RuleTrigger.CHARGING)
            Intent.ACTION_POWER_DISCONNECTED -> RuleScheduler.enqueue(context, RuleTrigger.NOT_CHARGING)
        }
    }
}
