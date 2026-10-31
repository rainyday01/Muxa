import XCTest

@MainActor
final class MuxaUITests: XCTestCase {
    func testPrimaryTabsAreAvailable() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()

        XCTAssertTrue(app.tabBars.buttons["Sessions"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.tabBars.buttons["Hosts"].exists)
        XCTAssertTrue(app.tabBars.buttons["Settings"].exists)
        app.tabBars.buttons["Settings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Appearance"].waitForExistence(timeout: 5))
        app.swipeUp()
        app.swipeUp()
        let about = app.descendants(matching: .any)["settings.about"]
        XCTAssertTrue(about.waitForExistence(timeout: 5) || app.staticTexts["Muxa"].waitForExistence(timeout: 2))
        app.tabBars.buttons["Hosts"].tap()
        let emptyState = app.descendants(matching: .any)["hosts.empty"]
        XCTAssertTrue(emptyState.waitForExistence(timeout: 5))
    }

    func testSessionsRefreshAndSwitchesToRecentView() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing", "-ui-testing-no-recent", "-ui-testing-preloaded-host"]
        app.launch()
        XCTAssertTrue(app.buttons["sessions.refresh"].waitForExistence(timeout: 5))
        app.buttons["sessions.refresh"].tap()

        XCTAssertTrue(app.staticTexts["demo"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["2 windows"].exists)
        let viewPicker = app.buttons["sessions.view-mode"]
        XCTAssertTrue(viewPicker.exists)
        viewPicker.tap()
        XCTAssertTrue(app.buttons["Recent"].waitForExistence(timeout: 5))
        app.buttons["Recent"].tap()
        XCTAssertTrue(app.staticTexts["demo"].exists)
    }

    func testCreatesSessionFromCodexTemplate() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing", "-ui-testing-no-recent", "-ui-testing-preloaded-host"]
        app.launch()
        XCTAssertTrue(app.staticTexts["Sessions"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["sessions.new"].waitForExistence(timeout: 5))
        app.buttons["sessions.new"].tap()
        XCTAssertTrue(app.navigationBars["New Session"].waitForExistence(timeout: 5))

        let templatePicker = app.buttons["session.creation.template"]
        XCTAssertTrue(templatePicker.exists)
        templatePicker.tap()
        XCTAssertTrue(app.buttons["Codex"].waitForExistence(timeout: 5))
        app.buttons["Codex"].tap()
        app.buttons["session.creation.save"].tap()

        XCTAssertTrue(app.alerts["Session"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.alerts["Session"].staticTexts["Created codex."].exists)
    }

    func testSessionDetailCopiesAttachCommandAndRenamesSession() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing", "-ui-testing-no-recent"]
        app.launch()
        app.tabBars.buttons["Hosts"].tap()
        addPasswordHost(app, alias: "Action Host")
        app.tabBars.buttons["Sessions"].tap()
        app.buttons["sessions.refresh"].tap()
        XCTAssertTrue(app.staticTexts["demo"].waitForExistence(timeout: 5))

        app.staticTexts["demo"].tap()
        XCTAssertTrue(app.navigationBars["demo"].waitForExistence(timeout: 5))
        app.buttons["session.detail.copy-attach"].tap()
        XCTAssertTrue(app.alerts["Session"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.alerts["Session"].staticTexts["Attach command copied."].exists)
        app.alerts["Session"].buttons["OK"].tap()

        app.staticTexts["demo"].tap()
        app.buttons["session.detail.rename"].tap()
        app.textFields["session.detail.name"].tap()
        app.typeText("renamed")
        app.buttons["session.detail.save-rename"].tap()
        XCTAssertTrue(app.alerts["Session"].waitForExistence(timeout: 5))
        XCTAssertTrue(
            app.alerts["Session"].staticTexts.matching(
                NSPredicate(format: "label CONTAINS %@", "Renamed")
            ).firstMatch.exists
        )
    }

    func testSessionDetailTerminatesWithConfirmation() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing", "-ui-testing-no-recent"]
        app.launch()
        app.tabBars.buttons["Hosts"].tap()
        addPasswordHost(app, alias: "Terminate Host")
        app.tabBars.buttons["Sessions"].tap()
        app.buttons["sessions.refresh"].tap()
        XCTAssertTrue(app.staticTexts["demo"].waitForExistence(timeout: 5))

        app.staticTexts["demo"].tap()
        app.buttons["session.detail.terminate"].tap()
        XCTAssertTrue(app.staticTexts["Terminate demo?"].waitForExistence(timeout: 5))
        app.buttons["Terminate Session"].tap()
        XCTAssertTrue(app.alerts["Session"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.alerts["Session"].staticTexts["Terminated demo."].exists)
    }

    func testTerminalProbeShowsLocalCompatibilityMatrix() {
        let app = XCUIApplication()
        app.launchArguments = ["-terminal-probe"]
        app.launch()

        let compatibility = app.staticTexts["terminal.probe.compatibility"]
        XCTAssertTrue(compatibility.waitForExistence(timeout: 5))
        XCTAssertTrue(compatibility.label.contains("vim"))
        XCTAssertTrue(compatibility.label.contains("Claude"))
        XCTAssertTrue(compatibility.label.contains("Codex"))
        XCTAssertTrue(compatibility.label.contains("Grok"))
    }

    func testSwiftTermProbeAcceptsKeyboardInput() {
        let app = XCUIApplication()
        app.launchArguments = ["-terminal-probe"]
        app.launch()

        let terminal = app.textViews["terminal.probe"]
        XCTAssertTrue(terminal.waitForExistence(timeout: 5))
        terminal.tap()
        app.typeText("muxa")

        let input = app.staticTexts["terminal.probe.input"]
        let captured = NSPredicate(format: "label CONTAINS %@", "muxa")
        expectation(for: captured, evaluatedWith: input)
        waitForExpectations(timeout: 5)
    }

    func testAddsPasswordHost() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        app.tabBars.buttons["Hosts"].tap()

        let addButton = app.buttons["hosts.add"]
        XCTAssertTrue(addButton.waitForExistence(timeout: 5))
        addButton.tap()
        XCTAssertTrue(app.navigationBars["Add Host"].waitForExistence(timeout: 5))

        app.textFields["host.editor.alias"].tap()
        app.typeText("Test Host")
        app.textFields["host.editor.hostname"].tap()
        app.typeText("example.invalid")
        app.textFields["host.editor.username"].tap()
        app.typeText("root")
        app.secureTextFields["host.editor.password"].tap()
        app.typeText("local-test-password")
        app.buttons["host.editor.save"].tap()
        dismissSystemPasswordPrompt(app)

        XCTAssertTrue(app.staticTexts["Test Host"].waitForExistence(timeout: 5))
    }

    func testHostConnectionPromptsForUnknownHostKey() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        app.tabBars.buttons["Hosts"].tap()
        app.buttons["hosts.add"].tap()

        app.textFields["host.editor.alias"].tap()
        app.typeText("Test Host")
        app.textFields["host.editor.hostname"].tap()
        app.typeText("example.invalid")
        app.textFields["host.editor.username"].tap()
        app.typeText("root")
        app.secureTextFields["host.editor.password"].tap()
        app.typeText("local-test-password")
        app.buttons["host.editor.save"].tap()
        dismissSystemPasswordPrompt(app)

        let action = app.buttons["Test Connection"]
        XCTAssertTrue(action.waitForExistence(timeout: 5))
        action.tap()

        XCTAssertTrue(app.alerts["Verify host key"].waitForExistence(timeout: 5))
        app.alerts["Verify host key"].buttons["Trust and connect"].tap()
        XCTAssertTrue(app.alerts["Connection"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.alerts["Connection"].staticTexts["Connected to Test Host. Found 1 session."].exists)
    }

    func testHostConnectionBlocksChangedHostKeyUntilReplacement() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing", "-ui-testing-host-key-mismatch"]
        app.launch()
        app.tabBars.buttons["Hosts"].tap()
        addPasswordHost(app, alias: "Changed Key Host")

        app.buttons["Test Connection"].tap()

        let alert = app.alerts["Host key changed"]
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        XCTAssertTrue(alert.buttons["Replace and connect"].exists)
        alert.buttons["Replace and connect"].tap()

        XCTAssertTrue(app.alerts["Connection"].waitForExistence(timeout: 5))
        XCTAssertTrue(
            app.alerts["Connection"].staticTexts["Connected to Changed Key Host. Found 1 session."].exists
        )
    }

    func testHostConnectionShowsAuthenticationFailure() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing", "-ui-testing-auth-failure"]
        app.launch()
        app.tabBars.buttons["Hosts"].tap()
        addPasswordHost(app, alias: "Auth Failure Host")

        app.buttons["Test Connection"].tap()

        XCTAssertTrue(app.alerts["Connection"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.alerts["Connection"].staticTexts["Authentication failed."].exists)
    }

    func testHostProbeUpdatesCapabilityStatus() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        app.tabBars.buttons["Hosts"].tap()
        app.buttons["hosts.add"].tap()

        app.textFields["host.editor.alias"].tap()
        app.typeText("Probe Host")
        app.textFields["host.editor.hostname"].tap()
        app.typeText("example.invalid")
        app.textFields["host.editor.username"].tap()
        app.typeText("root")
        app.secureTextFields["host.editor.password"].tap()
        app.typeText("local-test-password")
        app.buttons["host.editor.save"].tap()
        dismissSystemPasswordPrompt(app)

        let probe = app.buttons["Probe Host"]
        XCTAssertTrue(probe.waitForExistence(timeout: 5))
        probe.tap()
        XCTAssertTrue(app.alerts["Connection"].waitForExistence(timeout: 5))
        XCTAssertTrue(
            app.alerts["Connection"].staticTexts["Probe complete: tmux ready, 12 ms. Found 1 session."].exists
        )
    }

    func testHostProbePromptsForUnknownHostKey() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing", "-ui-testing-probe-host-key"]
        app.launch()
        app.tabBars.buttons["Hosts"].tap()
        addPasswordHost(app, alias: "Probe Trust Host")

        app.buttons["Probe Host"].tap()
        XCTAssertTrue(app.alerts["Verify host key"].waitForExistence(timeout: 5))
        app.alerts["Verify host key"].buttons["Trust and connect"].tap()

        XCTAssertTrue(app.alerts["Connection"].waitForExistence(timeout: 5))
        XCTAssertTrue(
            app.alerts["Connection"].staticTexts["Probe complete: tmux ready, 12 ms. Found 1 session."].exists
        )
    }

    func testDeletesHostAfterConfirmation() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launch()
        app.tabBars.buttons["Hosts"].tap()
        app.buttons["hosts.add"].tap()

        app.textFields["host.editor.alias"].tap()
        app.typeText("Delete Host")
        app.textFields["host.editor.hostname"].tap()
        app.typeText("example.invalid")
        app.textFields["host.editor.username"].tap()
        app.typeText("root")
        app.secureTextFields["host.editor.password"].tap()
        app.typeText("local-test-password")
        app.buttons["host.editor.save"].tap()
        dismissSystemPasswordPrompt(app)

        XCTAssertTrue(app.staticTexts["Delete Host"].waitForExistence(timeout: 5))
        let matchingRows = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "hosts.row.")
        )
        XCTAssertTrue(matchingRows.count >= 1)
        matchingRows.element(boundBy: 0).swipeLeft()

        let deleteButton = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "hosts.delete.")
        ).firstMatch
        XCTAssertTrue(deleteButton.waitForExistence(timeout: 5))
        deleteButton.tap()

        let confirmationTitle = app.staticTexts["Delete Delete Host?"]
        XCTAssertTrue(confirmationTitle.waitForExistence(timeout: 5))
        let confirmButton = app.buttons["Delete Host"]
        XCTAssertTrue(confirmButton.exists)
        confirmButton.tap()

        XCTAssertTrue(app.alerts["Connection"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.alerts["Connection"].staticTexts["Deleted Delete Host."].exists)
        XCTAssertFalse(app.staticTexts["Delete Host"].exists)
    }

    private func addPasswordHost(_ app: XCUIApplication, alias: String) {
        app.buttons["hosts.add"].tap()
        app.textFields["host.editor.alias"].tap()
        app.typeText(alias)
        app.textFields["host.editor.hostname"].tap()
        app.typeText("example.invalid")
        app.textFields["host.editor.username"].tap()
        app.typeText("root")
        app.secureTextFields["host.editor.password"].tap()
        app.typeText("local-test-password")
        app.buttons["host.editor.save"].tap()
        dismissSystemPasswordPrompt(app)
        XCTAssertTrue(app.staticTexts[alias].waitForExistence(timeout: 5))
    }

    private func dismissSystemPasswordPrompt(_ app: XCUIApplication) {
        for label in ["以后", "Later", "Not Now"] {
            let button = app.buttons[label]
            if button.waitForExistence(timeout: 1) {
                button.tap()
                return
            }
        }
    }
}
