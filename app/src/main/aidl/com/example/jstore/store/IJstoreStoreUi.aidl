// SPDX-FileCopyrightText: 2026 nguoimegame
// SPDX-License-Identifier: GPL-3.0-or-later
// Contract between JaecooApp and jstore, see docs/jstore-contract.md.
// Both repositories hold an identical copy of this file; change both together.
package com.example.jstore.store;

import android.os.Bundle;
import android.os.IBinder;
import com.example.jstore.store.IJstoreStoreUiCallback;

// Served by jstore: the store screen (Jaecoo catalogue, Google Play, app manager),
// drawn by jstore and shown inside JaecooApp through a SurfaceControlViewHost.
interface IJstoreStoreUi {
    int apiVersion(); // 1

    // Returns a Bundle holding the SurfaceControlViewHost.SurfacePackage under "surface_package".
    Bundle attach(
        IBinder hostToken,
        int displayId,
        int width,
        int height,
        in Bundle hostConfig,
        IJstoreStoreUiCallback callback
    );

    void relayout(int width, int height, in Bundle hostConfig);

    // "jaecoo" | "play" | "manage"
    void showSection(String section);

    void detach();
}
