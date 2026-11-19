// SPDX-FileCopyrightText: 2026 nguoimegame
// SPDX-License-Identifier: GPL-3.0-or-later
// Contract between JaecooApp and jstore, see docs/jstore-contract.md.
// Both repositories hold an identical copy of this file; change both together.
package com.example.jstore.store;

import android.content.Intent;

oneway interface IJstoreStoreUiCallback {
    // JaecooApp starts it only if it passes its intent filter (docs/jstore-contract.md).
    void onStartActivity(in Intent intent);

    void onRequestPhoneUpload();

    void onRequestJstoreUpdate();

    void onShowToast(String message);
}
