// SPDX-License-Identifier: GPL-3.0-or-later
package dev.lyo.callrec.telephony

import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import dev.lyo.callrec.core.L

/** Distinguishes a real SIM call from a self-managed VoIP call. */
object CellularCall {

    /**
     * True when at least one active subscription reports a non-idle call.
     * Failure is fail-open: uncertainty records rather than silently dropping
     * a real call.
     */
    fun isActive(ctx: Context): Boolean {
        val tm = ctx.getSystemService<TelephonyManager>() ?: return true
        return runCatching {
            val subIds = activeSubscriptionIds(ctx)
            if (subIds.isEmpty()) {
                return@runCatching tm.callStateForSubscription != TelephonyManager.CALL_STATE_IDLE
            }
            subIds.any { subId ->
                tm.createForSubscriptionId(subId).callStateForSubscription !=
                    TelephonyManager.CALL_STATE_IDLE
            }
        }.getOrElse {
            L.w("Receiver", "cellular probe failed (${it.javaClass.simpleName}) — assuming SIM call")
            true
        }
    }

    private fun activeSubscriptionIds(ctx: Context): List<Int> = runCatching {
        ctx.getSystemService<SubscriptionManager>()
            ?.activeSubscriptionInfoList
            ?.map { it.subscriptionId }
            .orEmpty()
    }.getOrDefault(emptyList())
}
