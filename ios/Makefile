SHELL := /bin/zsh

PROJECT := Muxa.xcodeproj
SCHEME := Muxa
DESTINATION := platform=iOS Simulator,name=iPhone 17 Pro,OS=latest
DERIVED_DATA := .build/DerivedData
RESULTS := .build/TestResults
ARCHIVE := .build/Archive/Muxa.xcarchive

.PHONY: bootstrap native-ssh generate build-sim test-unit test-ui test lint format-check secrets-scan license-check verify archive-local clean

bootstrap:
	@command -v xcodegen >/dev/null
	@command -v cmake >/dev/null
	@xcodebuild -checkFirstLaunchStatus
	$(MAKE) generate
	xcodebuild -resolvePackageDependencies -project $(PROJECT) -scheme $(SCHEME)

native-ssh:
	zsh scripts/build-native-ssh.sh

generate:
	xcodegen generate --spec project.yml

build-sim: generate
	xcodebuild build -project $(PROJECT) -scheme $(SCHEME) -destination '$(DESTINATION)' -derivedDataPath $(DERIVED_DATA) CODE_SIGNING_ALLOWED=NO

test-unit: generate
	mkdir -p $(RESULTS)
	rm -rf $(RESULTS)/Unit.xcresult
	xcodebuild test -project $(PROJECT) -scheme $(SCHEME) -destination '$(DESTINATION)' -derivedDataPath $(DERIVED_DATA) -only-testing:MuxaTests -resultBundlePath $(RESULTS)/Unit.xcresult

test-ui: generate
	mkdir -p $(RESULTS)
	rm -rf $(RESULTS)/UI.xcresult
	xcodebuild test -project $(PROJECT) -scheme $(SCHEME) -destination '$(DESTINATION)' -derivedDataPath $(DERIVED_DATA) -only-testing:MuxaUITests -resultBundlePath $(RESULTS)/UI.xcresult CODE_SIGNING_ALLOWED=NO

test: test-unit test-ui

lint:
	swiftlint lint --strict --config .swiftlint.yml

format-check:
	swiftformat Muxa MuxaTests MuxaUITests --lint

secrets-scan:
	# LogHubClient.swift embeds the Muxa upload token, matching the Android client.
	# Do not add an admin or read token to that file.
	@if rg -nU --hidden --glob '!*.xcresult/**' --glob '!.build/**' --glob '!Muxa/Infrastructure/Diagnostics/LogHubClient.swift' '(ghp_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|lh_(up|rd|ad)_[A-Za-z0-9_]+|-----BEGIN (RSA |OPENSSH |EC |ENCRYPTED )?PRIVATE KEY-----\\s+[A-Za-z0-9+/=\\s]{32,}-----END (RSA |OPENSSH |EC |ENCRYPTED )?PRIVATE KEY-----)' Muxa MuxaTests MuxaUITests; then \
		echo 'Potential credential material found'; exit 1; \
	fi

license-check:
	zsh scripts/check-licenses.sh

verify: build-sim test lint format-check secrets-scan license-check

archive-local: generate
	mkdir -p .build/Archive
	xcodebuild archive -project $(PROJECT) -scheme $(SCHEME) -destination 'generic/platform=iOS' -archivePath $(ARCHIVE) -derivedDataPath $(DERIVED_DATA) CODE_SIGNING_ALLOWED=NO

clean:
	xcodebuild clean -project $(PROJECT) -scheme $(SCHEME) -derivedDataPath $(DERIVED_DATA)
