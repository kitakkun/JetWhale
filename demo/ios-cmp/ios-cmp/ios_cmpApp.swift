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
            VStack(spacing: 0) {
                SwiftUIControlsBar()
                CMPAppViewControllerWrapper()
            }
            .ignoresSafeArea(.all)
        }
    }
}

/// SwiftUI controls above the Compose content: the iOS counterpart of the `AndroidView { }` samples,
/// so the Compose Semantics Inspector has SwiftUI nodes to capture and act on next to the Compose ones.
struct SwiftUIControlsBar: View {
    @State private var taps = 0
    @State private var isOn = false
    @State private var name = ""

    var body: some View {
        VStack(spacing: 8) {
            Text("Taps: \(taps)")
                .accessibilityIdentifier("swiftui-taps")
            HStack {
                Button("SwiftUI Button") { taps += 1 }
                    .accessibilityIdentifier("swiftui-button")
                Toggle("Flag", isOn: $isOn)
                    .accessibilityIdentifier("swiftui-toggle")
            }
            TextField("Name", text: $name)
                .textFieldStyle(.roundedBorder)
                .accessibilityIdentifier("swiftui-name")
        }
        .padding()
        .padding(.top, 60)
        .background(Color.yellow.opacity(0.3))
    }
}
