// SPDX-FileCopyrightText: 2026 nguoimegame
// SPDX-License-Identifier: GPL-3.0-or-later
// Contract between JaecooApp and jstore, see docs/jstore-contract.md.
// Both repositories hold an identical copy of this file; change both together.
package com.example.jaecooapp.installer;

import android.net.Uri;
import com.example.jaecooapp.installer.IInstallCallback;

// Served by JaecooApp, which installs as Device Owner on jstore's behalf.
interface IJaecooInstaller {
    int apiVersion();

    // apks: content:// URIs the caller has granted JaecooApp read access to.
    // Returns at once; callback.onResult is called exactly once.
    void install(String packageName, in List<Uri> apks, IInstallCallback callback);

    void uninstall(String packageName, IInstallCallback callback);
}
