package com.jackwallner.pelojack.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.jackwallner.pelojack.ui.Card
import com.jackwallner.pelojack.ui.Page
import com.jackwallner.pelojack.ui.Palette
import com.jackwallner.pelojack.ui.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class InstalledApp(val label: String, val icon: ImageBitmap, val launch: Intent)

private fun Context.installedApps(): List<InstalledApp> {
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(query, 0)
        .filter { it.activityInfo.packageName != packageName }
        .mapNotNull { info ->
            val launch = packageManager.getLaunchIntentForPackage(info.activityInfo.packageName) ?: return@mapNotNull null
            InstalledApp(
                label = info.loadLabel(packageManager).toString(),
                icon = info.loadIcon(packageManager).toBitmap(192, 192).asImageBitmap(),
                launch = launch,
            )
        }
        .distinctBy { it.launch.`package` }
        .sortedBy { it.label.lowercase() }
}

/** Every other app on the tablet, since Pelojack replaces the home screen. */
@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val apps by produceState(emptyList<InstalledApp>()) {
        value = withContext(Dispatchers.IO) { context.installedApps() }
    }
    Page("Apps", subtitle = "Everything else on the tablet") {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(200.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(apps) { app ->
                Card(onClick = { context.startActivity(app.launch) }, color = Palette.Background) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Image(app.icon, contentDescription = null, Modifier.size(96.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(app.label, style = Type.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
