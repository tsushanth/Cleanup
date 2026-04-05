import XCTest

@MainActor
class ScreenshotTests: XCTestCase {
    let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        setupSnapshot(app)
        app.launch()
    }

    func testScreenshots() {
        sleep(3)
        snapshot("01_Dashboard")

        app.tabBars.buttons["Photos"].tap()
        sleep(1)
        snapshot("02_Photos")

        app.tabBars.buttons["Videos"].tap()
        sleep(1)
        snapshot("03_Videos")

        app.tabBars.buttons["Contacts"].tap()
        sleep(1)
        snapshot("04_Contacts")

        app.tabBars.buttons["Settings"].tap()
        sleep(1)
        snapshot("05_Settings")
    }
}
