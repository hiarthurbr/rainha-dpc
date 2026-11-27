package br.arthur.rainha.dpc

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo

object InstalledApps {
    fun launchableApps(context: Context): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
    }
}
