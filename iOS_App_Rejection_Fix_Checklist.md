# iOS App Rejection Fix Checklist

**Submission ID:** aa71009b-8a90-47d8-bf65-b83d22c2b36c
**Review Date:** January 28, 2026
**App Version:** 1.0

---

## ✅ COMPLETED

### 1. Support URL - Guideline 1.5
**Status:** ✅ DONE

- **Issue:** Support URL (https://kreativekoala.llc/support) was not functional
- **Solution:** Created comprehensive support.html page at `/Users/sushanthtiruvaipati/Documents/GitHub/kreative-koala-legal/support.html`
- **Next Steps:**
  1. Deploy the website with the new support.html page
  2. Verify https://kreativekoala.llc/support is accessible
  3. The support page includes:
     - FAQs about subscriptions
     - Technical support information
     - Billing & refund information
     - Contact information (email, phone)
     - Links to Privacy Policy and Terms of Service

---

## 📋 ACTION REQUIRED

### 2. Terms of Use (EULA) - Guideline 3.1.2
**Status:** ⚠️ NEEDS ACTION

**Issue:** Missing Terms of Use (EULA) link in App Store metadata

**Solution:** Add Apple's standard EULA to your App Description

**Steps:**
1. Go to [App Store Connect](https://appstoreconnect.apple.com)
2. Navigate to your app
3. Click on "App Information" section
4. Scroll to "App Description" field
5. Add this text at the bottom of your description:

   ```
   Terms of Use: https://www.apple.com/legal/internet-services/itunes/dev/stdeula/
   ```

**Alternative:** If you want to use a custom EULA:
1. Go to "App Information" in App Store Connect
2. Scroll to "License Agreement" section
3. Upload your custom EULA text

---

### 3. In-App Purchase Unresponsiveness - Guideline 2.1 (CRITICAL)
**Status:** 🔴 CRITICAL - NEEDS INVESTIGATION

**Issue:** App becomes unresponsive after attempting to make a purchase on iPad Air (5th gen, iPadOS 26.2)

**What You Need to Do:**

#### A. Test in Sandbox Environment
1. Set up sandbox test users in App Store Connect
2. Test all IAP flows on iPad Air (or simulator)
3. Monitor for:
   - App freezing/hanging
   - Missing loading indicators
   - Errors in console logs
   - Transaction state issues

#### B. Common Causes to Check:
1. **Missing Loading States**
   - Add activity indicators during purchase flow
   - Disable purchase button while transaction is processing

2. **Transaction Observer Issues**
   - Ensure `SKPaymentTransactionObserver` is properly added
   - Check if transactions are being properly finished
   - Verify observer is not being removed prematurely

3. **Main Thread Blocking**
   - Ensure StoreKit operations don't block main thread
   - Use proper async/await or completion handlers

4. **Error Handling**
   - Check all error cases are handled
   - Verify app doesn't get stuck in error state

#### C. Debug Checklist:
```swift
// Example areas to review:

// 1. Add transaction observer in AppDelegate/App
SKPaymentQueue.default().add(transactionObserver)

// 2. Properly handle all transaction states
func paymentQueue(_ queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
    for transaction in transactions {
        switch transaction.transactionState {
        case .purchasing:
            // Show loading indicator
        case .purchased:
            // Deliver content, finish transaction
            SKPaymentQueue.default().finishTransaction(transaction)
        case .failed:
            // Handle error, finish transaction
            SKPaymentQueue.default().finishTransaction(transaction)
        case .restored:
            // Restore content, finish transaction
            SKPaymentQueue.default().finishTransaction(transaction)
        case .deferred:
            // Handle deferred (parental approval pending)
        @unknown default:
            break
        }
    }
}

// 3. Ensure UI updates happen on main thread
DispatchQueue.main.async {
    // Update UI here
}
```

#### D. Testing Steps:
1. Clean build and install on iPad Air (5th gen) or simulator
2. Attempt to purchase subscription
3. Monitor Xcode console for errors
4. Verify app responds after purchase attempt
5. Test both successful and cancelled purchase flows
6. Test on iPadOS 26.2 specifically

#### E. Required Fix Before Resubmission:
- [ ] Identify root cause of unresponsiveness
- [ ] Fix the issue
- [ ] Test thoroughly on iPad Air (5th gen) with iPadOS 26.2
- [ ] Verify all IAP flows work without freezing

---

### 4. Paid Apps Agreement - Guideline 2.1
**Status:** ⚠️ NEEDS VERIFICATION

**Issue:** IAP products require an active Paid Apps Agreement

**Steps:**
1. Go to [App Store Connect](https://appstoreconnect.apple.com)
2. Click on "Agreements, Tax, and Banking"
3. Verify "Paid Apps Agreement" shows status: **Active**
4. If not active:
   - Click "Request" to agree to the contract
   - Fill in all required tax and banking information
   - Submit for Apple review

**Note:** You cannot sell IAP products without this agreement signed and approved.

---

### 5. PassKit Framework - Guideline 2.1
**Status:** ⚠️ NEEDS CLARIFICATION

**Issue:** App binary includes PassKit framework, but reviewer couldn't find Apple Pay integration

**Solution:** Add a note in Review Notes for next submission

**Steps:**
1. Go to App Store Connect
2. Navigate to your app submission
3. In "App Review Information" section
4. In "Notes" field, add:

   ```
   Regarding PassKit framework:

   Our app does not directly implement Apple Pay functionality. The PassKit
   framework may be included as a transitive dependency from one of our third-party
   libraries. We do not use or expose any Apple Pay features in our app.

   If necessary, we can investigate removing this dependency in a future update.
   ```

**Alternative:** If you can easily remove the PassKit dependency:
1. Search your project for PassKit imports
2. Check Podfile/Package.swift for dependencies that might include PassKit
3. Remove if not needed
4. Clean build and verify PassKit is no longer in binary

---

## 📝 RESUBMISSION CHECKLIST

Before you resubmit to Apple, ensure you've completed:

- [ ] **Deploy support.html to website** - Test that https://kreativekoala.llc/support works
- [ ] **Add EULA link to App Description** in App Store Connect
- [ ] **Fix IAP unresponsiveness bug** - This is CRITICAL
- [ ] **Test IAP thoroughly** on iPad Air (5th gen) with iPadOS 26.2
- [ ] **Verify Paid Apps Agreement** is active
- [ ] **Add PassKit note to Review Notes** in submission

---

## 🚀 DEPLOYMENT STEPS

### Deploy Support Page to Website:

1. Navigate to your website repo:
   ```bash
   cd ~/Documents/GitHub/kreative-koala-legal
   ```

2. Commit the new support page:
   ```bash
   git add support.html styles.css
   git commit -m "Add support page for iOS app review compliance"
   git push
   ```

3. Deploy to your hosting service (based on your wrangler.toml, you're using Cloudflare Pages):
   ```bash
   npx wrangler pages deploy . --project-name=kreativekoala-legal
   ```

4. Verify the page is live:
   - Visit https://kreativekoala.llc/support
   - Check all links work
   - Test on mobile and desktop

---

## 📧 RECOMMENDED APP STORE CONNECT MESSAGE

When you resubmit, you can reply to Apple's review message with:

```
Hello App Review Team,

Thank you for the detailed feedback. We have addressed all the issues:

1. **Terms of Use (3.1.2):** Added Apple's standard EULA link to the App Description

2. **Support URL (1.5):** Fixed the support URL - https://kreativekoala.llc/support
   is now fully functional with FAQs, contact information, and support resources

3. **IAP Unresponsiveness (2.1):** [DESCRIBE YOUR FIX HERE - e.g., "Fixed a transaction
   observer issue that caused the app to freeze during purchase attempts. Added proper
   loading states and ensured all transactions are properly handled and finished."]

4. **Paid Apps Agreement:** Verified our Paid Apps Agreement is active and in good standing

5. **PassKit Framework:** Added clarification in Review Notes

We have thoroughly tested all changes on iPad Air (5th generation) running iPadOS 26.2.

Thank you for your patience.

Best regards,
Kreative Koala Team
```

---

## 🔍 NEED HELP?

For the IAP unresponsiveness issue (the most critical one), I recommend:

1. **Check your purchase flow code** - Look for where transactions are initiated
2. **Review transaction observer** - Ensure it's properly set up and handling all states
3. **Add logging** - Log every step of the purchase flow to identify where it hangs
4. **Test on physical iPad** - If possible, test on actual iPad Air 5th gen
5. **Check for memory leaks** - Ensure objects are properly released

Would you like me to:
- Review your IAP implementation code?
- Help debug the unresponsiveness issue?
- Create test scenarios for IAP testing?

---

**Last Updated:** 2026-01-31
