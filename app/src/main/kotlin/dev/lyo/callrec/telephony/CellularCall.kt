// SPDX-License-Identifier: GPL-3.0-or-later
package dev.lyo.callrec.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
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
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            L.w("Receiver", "READ_PHONE_STATE unavailable — assuming SIM call")
            return true
        }

        val tm = ctx.getSystemService<TelephonyManager>() ?: return true
        return try {
            val subIds = activeSubscriptionIds(ctx)
            if (subIds.isEmpty()) {
                tm.callStateForSubscription != TelephonyManager.CALL_STATE_IDLE
            } else {
                subIds.any { subId ->
                    tm.createForSubscriptionId(subId).callStateForSubscription !=
                        TelephonyManager.CALL_STATE_IDLE
                }
            }
        } catch (e: SecurityException) {
            L.w("Receiver", "cellular probe denied (${e.javaClass.simpleName}) — assuming SIM call")
            true
        } catch (e: RuntimeException) {
            L.w("Receiver", "cellular probe failed (${e.javaClass.simpleName}) — assuming SIM call")
            true
        }
    }

    private fun activeSubscriptionIds(ctx: Context): List<Int> {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        return try {
            ctx.getSystemService<SubscriptionManager>()
                ?.activeSubscriptionInfoList
                ?.map { it.subscriptionId }
                .orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}
