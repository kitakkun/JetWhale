//
//  CMPAppViewControllerWrapper.swift
//  ios-cmp
//
//  Created by kitakkun on 2026/01/24.
//

import SwiftUI
import shared

struct CMPAppViewControllerWrapper : UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> some UIViewController {
        return CMPAppViewControllerKt.cmpAppViewController()
    }
    
    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
        // do nothing
    }
}
