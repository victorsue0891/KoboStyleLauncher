package org.kobostyle.launcher

import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.Shadows

fun installSignaturePermission() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val permission = "${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    // Robolectric does not reproduce the install-time grant of our own signature permission.
    // Verify the merged manifest first so the grant cannot hide a production manifest omission.
    val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
    assertTrue(info.requestedPermissions.orEmpty().contains(permission))
    assertEquals(PermissionInfo.PROTECTION_SIGNATURE, app.packageManager.getPermissionInfo(permission, 0).protectionLevel)
    Shadows.shadowOf(app).grantPermissions(permission)
}
