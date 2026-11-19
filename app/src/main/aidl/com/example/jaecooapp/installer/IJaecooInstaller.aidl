// SPDX-FileCopyrightText: 2026 nguoimegame
// SPDX-License-Identifier: GPL-3.0-or-later
// Contract between JaecooApp and jstore, see docs/jstore-contract.md.
// Both repositories hold an identical copy of this file; change both together.
package com.example.jaecooapp.installer;

import android.net.Uri;
import android.os.Bundle;
import com.example.jaecooapp.installer.IInstallCallback;

// Served by JaecooApp, which installs as Device Owner on jstore's behalf.
interface IJaecooInstaller {
    int apiVersion();

    // apks: content:// URIs the caller has granted JaecooApp read access to.
    // Returns at once; callback.onResult is called exactly once.
    void install(String packageName, in List<Uri> apks, IInstallCallback callback);

    void uninstall(String packageName, IInstallCallback callback);

    // Added in apiVersion 2. Keep them last: transaction codes follow declaration order.

    // True when JaecooApp may hide apps (Device Owner or Profile Owner).
    boolean canManageApps();

    // The driver's apps, one Bundle each: packageName (String), label (String),
    // versionCode (long), versionName (String), hidden (boolean), protected (boolean),
    // icon (byte[] PNG, only for hidden apps). Blocking.
    List<Bundle> listApps();

    // False when refused (protected package, not allowed to manage) or failed.
    boolean setHidden(String packageName, boolean hidden);
}
