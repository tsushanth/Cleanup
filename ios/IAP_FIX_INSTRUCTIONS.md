# IAP Unresponsiveness Bug - Fix Instructions

## Issues Found & Fixes

### Issue 1: Unused SubscriptionManager.swift
**Problem:** A second, unused IAP manager exists with different product IDs.

**Fix:**
```bash
# Delete the unused file
rm ios/Cleanup/Subscription/SubscriptionManager.swift
```

Then remove it from Xcode project:
1. Open Xcode
2. Right-click `SubscriptionManager.swift` in the Project Navigator
3. Select "Delete" → "Move to Trash"

---

### Issue 2: iPad Sheet Presentation
**Problem:** Sheet presentation on iPad can cause unresponsiveness without proper presentation mode.

**Fix:** Update `ContentView.swift`

**Current code (line 41-43):**
```swift
.sheet(isPresented: $paywallCoordinator.showPaywall) {
    PaywallView()
}
```

**Replace with:**
```swift
.sheet(isPresented: $paywallCoordinator.showPaywall) {
    PaywallView()
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(entitlementManager.purchaseInProgress)
}
```

This ensures:
- Sheet uses `.large` detent (full screen on iPhone, proper sizing on iPad)
- Drag indicator is visible so users know they can dismiss
- Sheet can't be accidentally dismissed during purchase

---

### Issue 3: Add Timeout & Better Error Handling
**Problem:** No timeout for StoreKit operations that might hang.

**Fix:** Update `EntitlementManager.swift` purchase function

**Find this code (lines 113-145):**
```swift
@discardableResult
func purchase(_ product: Product) async -> Bool {
    purchaseInProgress = true
    lastError = nil
    defer { purchaseInProgress = false }

    do {
        let result = try await product.purchase()

        switch result {
        case .success(let verification):
            let transaction = try checkVerified(verification)
            await transaction.finish()
            await refreshEntitlements()
            return true

        case .pending:
            lastError = "Purchase is pending approval from a family organizer or guardian."
            return false

        case .userCancelled:
            // User cancelled - not an error
            return false

        @unknown default:
            return false
        }
    } catch {
        lastError = "Purchase failed: \(error.localizedDescription)"
        return false
    }
}
```

**Replace with:**
```swift
@discardableResult
func purchase(_ product: Product) async -> Bool {
    purchaseInProgress = true
    lastError = nil
    defer {
        Task { @MainActor in
            purchaseInProgress = false
        }
    }

    do {
        // Add timeout wrapper
        let result = try await withTimeout(seconds: 60) {
            try await product.purchase()
        }

        switch result {
        case .success(let verification):
            let transaction = try checkVerified(verification)

            // Ensure transaction finish happens on background
            await transaction.finish()

            // Refresh entitlements on main actor
            await MainActor.run {
                Task {
                    await refreshEntitlements()
                }
            }
            return true

        case .pending:
            await MainActor.run {
                lastError = "Purchase is pending approval from a family organizer or guardian."
            }
            return false

        case .userCancelled:
            // User cancelled - not an error
            return false

        @unknown default:
            return false
        }
    } catch is TimeoutError {
        await MainActor.run {
            lastError = "Purchase timed out. Please check your connection and try again."
        }
        return false
    } catch {
        await MainActor.run {
            lastError = "Purchase failed: \(error.localizedDescription)"
        }
        return false
    }
}

// Add this helper at the bottom of EntitlementManager.swift
private struct TimeoutError: Error {}

private func withTimeout<T>(seconds: TimeInterval, operation: @escaping () async throws -> T) async throws -> T {
    try await withThrowingTaskGroup(of: T.self) { group in
        group.addTask {
            try await operation()
        }

        group.addTask {
            try await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            throw TimeoutError()
        }

        let result = try await group.next()!
        group.cancelAll()
        return result
    }
}
```

---

### Issue 4: Explicit Main Thread UI Updates in PaywallView
**Problem:** After purchase completes, dismiss might not happen on main thread on iPad.

**Fix:** Update `PaywallView.swift` purchase handler

**Find this code (lines 116-123):**
```swift
private func purchaseSelected() async {
    guard let product = selectedProduct else { return }

    let success = await entitlementManager.purchase(product)
    if success {
        handleSuccessfulPurchase()
    }
}
```

**Replace with:**
```swift
private func purchaseSelected() async {
    guard let product = selectedProduct else { return }

    let success = await entitlementManager.purchase(product)

    // Ensure UI update happens on main thread
    await MainActor.run {
        if success {
            handleSuccessfulPurchase()
        }
    }
}
```

---

### Issue 5: Add Retry Mechanism for Failed Product Loading
**Problem:** If products fail to load, user is stuck.

**Fix:** Update `PaywallView.swift` to add retry button

**Find the PlanPickerView code (lines 220-233):**
```swift
if products.isEmpty {
    VStack(spacing: 12) {
        Image(systemName: "exclamationmark.triangle")
            .font(.title)
            .foregroundColor(.orange)
        Text("Unable to load plans")
            .font(.subheadline)
            .foregroundColor(.secondary)
        Text("Please check your connection and try again")
            .font(.caption)
            .foregroundColor(.secondary)
    }
    .frame(maxWidth: .infinity)
    .padding(.vertical, 40)
}
```

**Replace with:**
```swift
if products.isEmpty {
    VStack(spacing: 12) {
        Image(systemName: "exclamationmark.triangle")
            .font(.title)
            .foregroundColor(.orange)
        Text("Unable to load plans")
            .font(.subheadline)
            .foregroundColor(.secondary)
        Text("Please check your connection and try again")
            .font(.caption)
            .foregroundColor(.secondary)

        Button {
            Task {
                await entitlementManager.loadProducts()
            }
        } label: {
            Label("Retry", systemImage: "arrow.clockwise")
                .font(.subheadline)
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
                .background(Color.blue)
                .foregroundColor(.white)
                .cornerRadius(8)
        }
        .padding(.top, 8)
    }
    .frame(maxWidth: .infinity)
    .padding(.vertical, 40)
}
```

---

### Issue 6: Ensure Product IDs Match App Store Connect
**CRITICAL:** Verify your App Store Connect configuration

**Required Steps:**
1. Go to [App Store Connect](https://appstoreconnect.apple.com)
2. Navigate to your app → Features → In-App Purchases
3. Verify these exact product IDs exist and are approved:
   - `cleanup_pro_weekly_799`
   - `cleanup_pro_monthly_999`
   - `cleanup_pro_yearly_2999`

4. If they don't exist or have different names:
   - Either create them with these exact IDs in App Store Connect
   - OR update `EntitlementManager.swift` lines 16-20 to match your actual product IDs

---

## Testing Checklist

After applying all fixes:

- [ ] Delete SubscriptionManager.swift
- [ ] Update ContentView.swift sheet presentation
- [ ] Update EntitlementManager.swift purchase function
- [ ] Update PaywallView.swift purchase handler
- [ ] Add retry button to PlanPickerView
- [ ] Verify product IDs in App Store Connect
- [ ] Clean build (Cmd+Shift+K)
- [ ] Build and run on iPad Air (5th gen) simulator with iPadOS 26.2
- [ ] Test purchase flow:
  - [ ] Tap purchase button
  - [ ] Verify loading indicator appears
  - [ ] Complete purchase in sandbox
  - [ ] Verify app doesn't freeze
  - [ ] Verify sheet dismisses after purchase
  - [ ] Verify premium features unlock
- [ ] Test error scenarios:
  - [ ] Cancel purchase (should return to paywall, not freeze)
  - [ ] No internet connection (should show timeout error)
  - [ ] Failed product loading (should show retry button)
- [ ] Test on physical iPad Air if available

---

## Quick Fix Script

Run this from your project root to make the changes:

```bash
cd ~/Documents/GitHub/Cleanup/ios

# Backup current files
cp Cleanup/Subscription/EntitlementManager.swift Cleanup/Subscription/EntitlementManager.swift.backup
cp Cleanup/Subscription/PaywallView.swift Cleanup/Subscription/PaywallView.swift.backup
cp Cleanup/App/ContentView.swift Cleanup/App/ContentView.swift.backup

# Remove unused SubscriptionManager
rm Cleanup/Subscription/SubscriptionManager.swift

echo "✅ Deleted SubscriptionManager.swift"
echo "⚠️  Now apply the code changes manually using the instructions above"
echo "📝 Backups created with .backup extension"
```

---

## Expected Behavior After Fixes

1. **User taps purchase button**
   - Loading indicator appears immediately
   - Purchase button is disabled
   - Sheet cannot be dismissed

2. **Purchase processing**
   - StoreKit popup appears for confirmation
   - User confirms or cancels

3. **Purchase completes**
   - Loading indicator disappears
   - Sheet dismisses smoothly
   - User returns to app with premium unlocked
   - No freezing or unresponsiveness

4. **Purchase fails/cancelled**
   - Loading indicator disappears
   - Error message shown (if failed)
   - Sheet remains open for retry
   - App remains responsive

---

## Additional Debugging

If issues persist after fixes, add this logging:

```swift
// In EntitlementManager.swift purchase function, add after line 120:
print("🛒 Starting purchase for product: \(product.id)")

// After line 125:
print("✅ Purchase successful, finishing transaction")

// After line 127:
print("✅ Entitlements refreshed")

// In catch block:
print("❌ Purchase error: \(error)")
```

Then test on iPad and check Xcode console for where it gets stuck.
