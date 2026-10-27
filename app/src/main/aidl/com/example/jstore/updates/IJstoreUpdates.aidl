// SPDX-FileCopyrightText: 2026 nguoimegame
// SPDX-License-Identifier: GPL-3.0-or-later
// Contract between JaecooApp and jstore, see docs/jstore-contract.md.
// Both repositories hold an identical copy of this file; change both together.
package com.example.jstore.updates;

import android.os.Bundle;
import com.example.jstore.updates.IDownloadCallback;

// Served by jstore: Google Play versions and downloads for JaecooApp's app manager.
interface IJstoreUpdates {
    int apiVersion();

    // Blocking. One Bundle per package Play knows: packageName (String),
    // versionCode (long), versionName (String), isFree (boolean).
    // Empty when not signed in or Play cannot be reached.
    List<Bundle> latestVersions(in List<String> packages);

    // Returns at once; the callback gets progress, then onReady or onError.
    void download(String packageName, IDownloadCallback callback);

    // Deletes the files of a finished download and revokes access to them.
    void release(String packageName);
}
