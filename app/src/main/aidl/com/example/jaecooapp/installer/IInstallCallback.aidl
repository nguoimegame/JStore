// SPDX-FileCopyrightText: 2026 nguoimegame
// SPDX-License-Identifier: GPL-3.0-or-later
// Contract between JaecooApp and jstore, see docs/jstore-contract.md.
package com.example.jaecooapp.installer;

oneway interface IInstallCallback {
    // status: 0 installed, 1 system confirmation shown, 2 failed, 3 uninstalled.
    void onResult(int status, String message);
}
