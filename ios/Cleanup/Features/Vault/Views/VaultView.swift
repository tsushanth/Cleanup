import SwiftUI
import LocalAuthentication

struct VaultView: View {
    @StateObject private var viewModel = VaultViewModel()
    @State private var isUnlocked = false
    @State private var enteredPin = ""
    @State private var showSetupPin = false

    var body: some View {
        Group {
            if isUnlocked {
                VaultContentView(viewModel: viewModel) {
                    isUnlocked = false
                }
            } else {
                VaultLockView(
                    enteredPin: $enteredPin,
                    viewModel: viewModel,
                    onUnlock: {
                        isUnlocked = true
                    },
                    onSetupPin: {
                        showSetupPin = true
                    }
                )
            }
        }
        .sheet(isPresented: $showSetupPin) {
            SetupPinView(viewModel: viewModel) {
                showSetupPin = false
            }
        }
    }
}

// MARK: - Lock View
struct VaultLockView: View {
    @Binding var enteredPin: String
    @ObservedObject var viewModel: VaultViewModel
    let onUnlock: () -> Void
    let onSetupPin: () -> Void

    @State private var showError = false
    @State private var errorMessage = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 30) {
                Spacer()

                // Lock Icon
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 80))
                    .foregroundColor(.blue)

                Text("Secret Space")
                    .font(.title)
                    .fontWeight(.bold)

                Text("Enter your PIN to access")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                // PIN Display
                HStack(spacing: 20) {
                    ForEach(0..<4, id: \.self) { index in
                        Circle()
                            .fill(index < enteredPin.count ? Color.blue : Color.gray.opacity(0.3))
                            .frame(width: 16, height: 16)
                    }
                }
                .padding()

                // Number Pad
                NumberPadView(enteredPin: $enteredPin, maxLength: 4) { pin in
                    verifyPin(pin)
                }

                Spacer()

                // Biometric Button
                if viewModel.canUseBiometrics {
                    Button(action: {
                        authenticateWithBiometrics()
                    }) {
                        HStack {
                            Image(systemName: viewModel.biometricType == .faceID ? "faceid" : "touchid")
                            Text("Use \(viewModel.biometricType == .faceID ? "Face ID" : "Touch ID")")
                        }
                        .foregroundColor(.blue)
                    }
                    .padding(.bottom, 30)
                }
            }
            .navigationTitle("Vault")
            .navigationBarTitleDisplayMode(.inline)
            .alert("Error", isPresented: $showError) {
                Button("OK", role: .cancel) {
                    enteredPin = ""
                }
            } message: {
                Text(errorMessage)
            }
            .onAppear {
                Task {
                    let hasPin = await viewModel.hasPin()
                    if !hasPin {
                        onSetupPin()
                    } else if viewModel.canUseBiometrics {
                        authenticateWithBiometrics()
                    }
                }
            }
        }
    }

    private func verifyPin(_ pin: String) {
        Task {
            let isValid = await viewModel.verifyPin(pin)
            if isValid {
                onUnlock()
            } else {
                errorMessage = "Incorrect PIN. Please try again."
                showError = true
            }
        }
    }

    private func authenticateWithBiometrics() {
        Task {
            let success = await viewModel.authenticateWithBiometrics()
            if success {
                onUnlock()
            }
        }
    }
}

// MARK: - Number Pad
struct NumberPadView: View {
    @Binding var enteredPin: String
    let maxLength: Int
    let onComplete: (String) -> Void

    let numbers = [
        ["1", "2", "3"],
        ["4", "5", "6"],
        ["7", "8", "9"],
        ["", "0", "⌫"]
    ]

    var body: some View {
        VStack(spacing: 16) {
            ForEach(numbers, id: \.self) { row in
                HStack(spacing: 24) {
                    ForEach(row, id: \.self) { number in
                        NumberButton(number: number) {
                            handleTap(number)
                        }
                    }
                }
            }
        }
    }

    private func handleTap(_ number: String) {
        if number == "⌫" {
            if !enteredPin.isEmpty {
                enteredPin.removeLast()
            }
        } else if !number.isEmpty {
            if enteredPin.count < maxLength {
                enteredPin.append(number)
                if enteredPin.count == maxLength {
                    onComplete(enteredPin)
                }
            }
        }
    }
}

struct NumberButton: View {
    let number: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(number)
                .font(.title)
                .fontWeight(.medium)
                .frame(width: 70, height: 70)
                .background(number.isEmpty ? Color.clear : Color(.systemGray5))
                .foregroundColor(.primary)
                .clipShape(Circle())
        }
        .disabled(number.isEmpty)
    }
}

// MARK: - Setup PIN View
struct SetupPinView: View {
    @ObservedObject var viewModel: VaultViewModel
    let onComplete: () -> Void

    @State private var pin = ""
    @State private var confirmPin = ""
    @State private var isConfirming = false
    @State private var showError = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 30) {
                Spacer()

                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 60))
                    .foregroundColor(.blue)

                Text(isConfirming ? "Confirm Your PIN" : "Create Your PIN")
                    .font(.title2)
                    .fontWeight(.bold)

                // PIN Display
                HStack(spacing: 20) {
                    ForEach(0..<4, id: \.self) { index in
                        let currentPin = isConfirming ? confirmPin : pin
                        Circle()
                            .fill(index < currentPin.count ? Color.blue : Color.gray.opacity(0.3))
                            .frame(width: 16, height: 16)
                    }
                }
                .padding()

                // Number Pad
                NumberPadView(
                    enteredPin: isConfirming ? $confirmPin : $pin,
                    maxLength: 4
                ) { _ in
                    handlePinEntry()
                }

                Spacer()
            }
            .navigationTitle("Setup PIN")
            .navigationBarTitleDisplayMode(.inline)
            .alert("PINs Don't Match", isPresented: $showError) {
                Button("Try Again", role: .cancel) {
                    pin = ""
                    confirmPin = ""
                    isConfirming = false
                }
            } message: {
                Text("The PINs you entered don't match. Please try again.")
            }
        }
    }

    private func handlePinEntry() {
        if !isConfirming {
            isConfirming = true
        } else {
            if pin == confirmPin {
                Task {
                    await viewModel.setPin(pin)
                    onComplete()
                }
            } else {
                showError = true
            }
        }
    }
}

// MARK: - Vault Content View
struct VaultContentView: View {
    @ObservedObject var viewModel: VaultViewModel
    let onLock: () -> Void

    @State private var showAddSheet = false
    @State private var selectedItem: VaultItem?

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.items.isEmpty {
                    VStack(spacing: 20) {
                        Image(systemName: "photo.on.rectangle.angled")
                            .font(.system(size: 60))
                            .foregroundColor(.gray)

                        Text("Your vault is empty")
                            .font(.headline)

                        Text("Add photos and videos to keep them private")
                            .font(.subheadline)
                            .foregroundColor(.secondary)

                        Button("Add Items") {
                            showAddSheet = true
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        LazyVGrid(columns: [
                            GridItem(.flexible()),
                            GridItem(.flexible()),
                            GridItem(.flexible())
                        ], spacing: 4) {
                            ForEach(viewModel.items) { item in
                                VaultItemThumbnail(item: item) {
                                    selectedItem = item
                                }
                            }
                        }
                        .padding(4)
                    }
                }
            }
            .navigationTitle("Secret Space")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: onLock) {
                        Image(systemName: "lock.fill")
                    }
                }

                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showAddSheet = true }) {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showAddSheet) {
                AddToVaultView(viewModel: viewModel)
            }
            .sheet(item: $selectedItem) { item in
                VaultItemDetailView(item: item, viewModel: viewModel)
            }
            .onAppear {
                viewModel.loadItems()
            }
        }
    }
}

struct VaultItemThumbnail: View {
    let item: VaultItem
    let onTap: () -> Void

    var body: some View {
        ZStack {
            if let thumbnailData = item.thumbnailData,
               let uiImage = UIImage(data: thumbnailData) {
                Image(uiImage: uiImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else {
                Rectangle()
                    .fill(Color.gray.opacity(0.3))
                    .overlay {
                        Image(systemName: item.fileType.icon)
                            .font(.title)
                            .foregroundColor(.gray)
                    }
            }

            if item.fileType == .video {
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        Image(systemName: "play.fill")
                            .font(.caption)
                            .foregroundColor(.white)
                            .padding(4)
                            .background(Color.black.opacity(0.5))
                            .clipShape(Circle())
                    }
                }
                .padding(4)
            }
        }
        .aspectRatio(1, contentMode: .fill)
        .clipped()
        .onTapGesture {
            onTap()
        }
    }
}

// MARK: - Add to Vault View
struct AddToVaultView: View {
    @ObservedObject var viewModel: VaultViewModel
    @Environment(\.dismiss) var dismiss
    @State private var showPhotoPicker = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Text("Select items to add to your vault")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                Button(action: { showPhotoPicker = true }) {
                    HStack {
                        Image(systemName: "photo.on.rectangle")
                        Text("Select from Photos")
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(12)
                }

                Spacer()
            }
            .padding()
            .navigationTitle("Add to Vault")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
            .sheet(isPresented: $showPhotoPicker) {
                PhotoPickerView { assets in
                    Task {
                        for asset in assets {
                            try? await viewModel.addToVault(asset: asset, deleteOriginal: true)
                        }
                        dismiss()
                    }
                }
            }
        }
    }
}

// MARK: - Vault Item Detail View
struct VaultItemDetailView: View {
    let item: VaultItem
    @ObservedObject var viewModel: VaultViewModel
    @Environment(\.dismiss) var dismiss
    @State private var showDeleteConfirmation = false
    @State private var showRestoreConfirmation = false

    var body: some View {
        NavigationStack {
            VStack {
                // Display image/video
                if let thumbnailData = item.thumbnailData,
                   let uiImage = UIImage(data: thumbnailData) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(maxHeight: 400)
                }

                // Info
                VStack(alignment: .leading, spacing: 8) {
                    Text(item.fileName)
                        .font(.headline)
                    Text("Added: \(item.addedDateFormatted)")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Text("Size: \(item.fileSizeFormatted)")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()

                Spacer()

                // Actions
                HStack(spacing: 20) {
                    Button(action: { showRestoreConfirmation = true }) {
                        VStack {
                            Image(systemName: "arrow.uturn.backward")
                            Text("Restore")
                        }
                    }

                    Button(action: { showDeleteConfirmation = true }) {
                        VStack {
                            Image(systemName: "trash")
                            Text("Delete")
                        }
                        .foregroundColor(.red)
                    }
                }
                .padding()
            }
            .navigationTitle("Item Details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
            .alert("Delete Item", isPresented: $showDeleteConfirmation) {
                Button("Cancel", role: .cancel) {}
                Button("Delete", role: .destructive) {
                    Task {
                        try? await viewModel.removeFromVault(item, restoreToPhotos: false)
                        dismiss()
                    }
                }
            } message: {
                Text("This will permanently delete the item from your vault.")
            }
            .alert("Restore Item", isPresented: $showRestoreConfirmation) {
                Button("Cancel", role: .cancel) {}
                Button("Restore") {
                    Task {
                        try? await viewModel.removeFromVault(item, restoreToPhotos: true)
                        dismiss()
                    }
                }
            } message: {
                Text("This will restore the item to your Photos library.")
            }
        }
    }
}

// MARK: - Photo Picker
import PhotosUI

struct PhotoPickerView: UIViewControllerRepresentable {
    let onSelect: ([PHAsset]) -> Void

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration(photoLibrary: .shared())
        config.selectionLimit = 0
        config.filter = .any(of: [.images, .videos])

        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onSelect: onSelect)
    }

    class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let onSelect: ([PHAsset]) -> Void

        init(onSelect: @escaping ([PHAsset]) -> Void) {
            self.onSelect = onSelect
        }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            picker.dismiss(animated: true)

            let identifiers = results.compactMap(\.assetIdentifier)
            let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: identifiers, options: nil)

            var assets: [PHAsset] = []
            fetchResult.enumerateObjects { asset, _, _ in
                assets.append(asset)
            }

            onSelect(assets)
        }
    }
}

#Preview {
    VaultView()
}
