//
//  ios_cmpApp.swift
//  ios-cmp
//
//  Created by kitakkun on 2026/01/24.
//

import SwiftUI
import shared

@main
struct ios_cmpApp: App {
    init() {
        InitializeJetWhaleKt.initializeJetWhale()
    }

    var body: some Scene {
        WindowGroup {
            CMPAppViewControllerWrapper()
                .ignoresSafeArea(.all)
        }
    }
}
