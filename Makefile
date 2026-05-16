# Heirlooms — top-level Makefile
#
# Usage:
#   make test-farm-smoke    Run J12 + J1 + CryptoSmokeTest on farm-current.
#   make test-farm-full     Run the full suite on all three devices.
#
# Prerequisites:
#   - scripts/test-farm/.env populated (copy from .env.template)
#   - scripts/test-farm/.farm-env will be created by provision-accounts.sh
#   - STAGING_APK path correct (adjust if you build release instead of debug)
#   - ADB, Maestro, and gcloud CLI on PATH
#   - gcloud ADC configured: gcloud auth application-default login

STAGING_APK ?= HeirloomsApp/app/build/outputs/apk/staging/debug/app-staging-debug.apk
SCRIPT_DIR  := scripts/test-farm

# ── Helpers ──────────────────────────────────────────────────────────────────

.PHONY: build-staging check-devices deploy-staging provision teardown

build-staging:
	./gradlew :HeirloomsApp:app:assembleStaging

check-devices:
	bash $(SCRIPT_DIR)/check-devices.sh

deploy-staging: build-staging check-devices
	bash $(SCRIPT_DIR)/deploy-apk.sh \
		--apk $(STAGING_APK) \
		--device $$(source $(SCRIPT_DIR)/.env && echo $$FARM_DEVICE_CURRENT)

deploy-all: build-staging check-devices
	bash $(SCRIPT_DIR)/deploy-all.sh --apk $(STAGING_APK)

provision:
	bash $(SCRIPT_DIR)/provision-accounts.sh

teardown:
	bash $(SCRIPT_DIR)/teardown-accounts.sh

# ── Smoke suite (PR gate — farm-current only) ─────────────────────────────────

.PHONY: test-farm-smoke

test-farm-smoke: deploy-staging provision
	@source $(SCRIPT_DIR)/.env && \
	source $(SCRIPT_DIR)/.farm-env && \
	adb -s $$FARM_DEVICE_CURRENT push \
		$(SCRIPT_DIR)/test-assets/test-photo.jpg \
		/sdcard/Pictures/heirlooms-test-photo.jpg && \
	mkdir -p test-results/farm/$$RUN_ID && \
	maestro test \
		--device $$FARM_DEVICE_CURRENT \
		--env USER_A_PASSWORD=$$USER_A_PASSWORD \
		--env USER_A_USERNAME=$$USER_A_USERNAME \
		--format junit \
		--output test-results/farm/$$RUN_ID/journey-12.xml \
		$(SCRIPT_DIR)/maestro/journey-12-flavor-smoke.yaml && \
	maestro test \
		--device $$FARM_DEVICE_CURRENT \
		--env USER_A_PASSWORD=$$USER_A_PASSWORD \
		--env USER_A_USERNAME=$$USER_A_USERNAME \
		--format junit \
		--output test-results/farm/$$RUN_ID/journey-01.xml \
		$(SCRIPT_DIR)/maestro/journey-01-upload-garden.yaml && \
	./gradlew :HeirloomsApp:app:connectedAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=digital.heirlooms.crypto.CryptoSmokeTest \
		-Pandroid.injected.invocation.noshards=true \
		-Pandroid.device.serial=$$FARM_DEVICE_CURRENT
	$(MAKE) teardown

# ── Full suite (nightly — all three devices) ──────────────────────────────────

.PHONY: test-farm-full

test-farm-full: deploy-all provision
	@source $(SCRIPT_DIR)/.env && \
	source $(SCRIPT_DIR)/.farm-env && \
	mkdir -p test-results/farm/$$RUN_ID && \
	for serial_var in FARM_DEVICE_MINAPI FARM_DEVICE_CURRENT FARM_DEVICE_LATEST; do \
		serial=$${!serial_var} ; \
		adb -s $$serial push \
			$(SCRIPT_DIR)/test-assets/test-photo.jpg \
			/sdcard/Pictures/heirlooms-test-photo.jpg ; \
	done && \
	maestro test \
		--device $$FARM_DEVICE_MINAPI \
		--env USER_A_PASSWORD=$$USER_A_PASSWORD \
		--env USER_A_USERNAME=$$USER_A_USERNAME \
		--format junit \
		--output test-results/farm/$$RUN_ID/journey-12-minapi.xml \
		$(SCRIPT_DIR)/maestro/journey-12-flavor-smoke.yaml & \
	maestro test \
		--device $$FARM_DEVICE_CURRENT \
		--env USER_A_PASSWORD=$$USER_A_PASSWORD \
		--env USER_A_USERNAME=$$USER_A_USERNAME \
		--format junit \
		--output test-results/farm/$$RUN_ID/journey-12-current.xml \
		$(SCRIPT_DIR)/maestro/journey-12-flavor-smoke.yaml & \
	maestro test \
		--device $$FARM_DEVICE_LATEST \
		--env USER_A_PASSWORD=$$USER_A_PASSWORD \
		--env USER_A_USERNAME=$$USER_A_USERNAME \
		--format junit \
		--output test-results/farm/$$RUN_ID/journey-12-latest.xml \
		$(SCRIPT_DIR)/maestro/journey-12-flavor-smoke.yaml & \
	wait && \
	maestro test \
		--device $$FARM_DEVICE_MINAPI \
		--env USER_A_PASSWORD=$$USER_A_PASSWORD \
		--env USER_A_USERNAME=$$USER_A_USERNAME \
		--format junit \
		--output test-results/farm/$$RUN_ID/journey-01-minapi.xml \
		$(SCRIPT_DIR)/maestro/journey-01-upload-garden.yaml & \
	maestro test \
		--device $$FARM_DEVICE_CURRENT \
		--env USER_A_PASSWORD=$$USER_A_PASSWORD \
		--env USER_A_USERNAME=$$USER_A_USERNAME \
		--format junit \
		--output test-results/farm/$$RUN_ID/journey-01-current.xml \
		$(SCRIPT_DIR)/maestro/journey-01-upload-garden.yaml & \
	maestro test \
		--device $$FARM_DEVICE_LATEST \
		--env USER_A_PASSWORD=$$USER_A_PASSWORD \
		--env USER_A_USERNAME=$$USER_A_USERNAME \
		--format junit \
		--output test-results/farm/$$RUN_ID/journey-01-latest.xml \
		$(SCRIPT_DIR)/maestro/journey-01-upload-garden.yaml & \
	wait && \
	for serial_var in FARM_DEVICE_MINAPI FARM_DEVICE_CURRENT FARM_DEVICE_LATEST; do \
		serial=$${!serial_var} ; \
		./gradlew :HeirloomsApp:app:connectedAndroidTest \
			-Pandroid.testInstrumentationRunnerArguments.class=digital.heirlooms.crypto.CryptoSmokeTest \
			-Pandroid.injected.invocation.noshards=true \
			-Pandroid.device.serial=$$serial ; \
	done
	$(MAKE) teardown
