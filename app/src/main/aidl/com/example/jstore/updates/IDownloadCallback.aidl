// SPDX-FileCopyrightText: 2026 nguoimegame
// SPDX-License-Identifier: GPL-3.0-or-later
// Contract between JaecooApp and jstore, see docs/jstore-contract.md.
package com.example.jstore.updates;

import android.net.Uri;

oneway interface IDownloadCallback {
    void onProgress(int percent);

    // content:// URIs readable by JaecooApp, base APK first.
    void onReady(in List<Uri> apks);

    // A message ready to show to the driver, in jstore's language.
    void onError(String message);
}
