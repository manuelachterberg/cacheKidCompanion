import {
  calculateArrowDegrees,
  calculateBearing,
  calculateDistanceMeters,
  normalizeDegrees,
  parseTarget,
} from "./navigation.js";

(function () {
  const state = {
    target: { latitude: 52.520008, longitude: 13.404954 },
    headingDegrees: null,
    location: null,
    bearingDegrees: null,
    arrowDegrees: 0,
    sourceLabel: "Browser",
    watchId: null,
    hasNativeHost: typeof window.AndroidHost !== "undefined",
    pendingMissionDraft: null,
    pendingResolution: null,
    importStatus: null,
    shareDebug: null,
  };

  const ui = {
    arrow: document.getElementById("arrow"),
    distanceValue: document.getElementById("distanceValue"),
    headingValue: document.getElementById("headingValue"),
    bearingValue: document.getElementById("bearingValue"),
    sourceValue: document.getElementById("sourceValue"),
    locationValue: document.getElementById("locationValue"),
    resolutionPanel: document.getElementById("resolutionPanel"),
    resolutionMessage: document.getElementById("resolutionMessage"),
    resolutionCacheCode: document.getElementById("resolutionCacheCode"),
    resolutionTitleInput: document.getElementById("resolutionTitleInput"),
    resolutionTargetInput: document.getElementById("resolutionTargetInput"),
    resolveManuallyButton: document.getElementById("resolveManuallyButton"),
    prefillImportedTargetButton: document.getElementById("prefillImportedTargetButton"),
    importStatusBanner: document.getElementById("importStatusBanner"),
    importStatusText: document.getElementById("importStatusText"),
    shareDebugText: document.getElementById("shareDebugText"),
    importReviewPanel: document.getElementById("importReviewPanel"),
    importCacheCode: document.getElementById("importCacheCode"),
    importSourceTitle: document.getElementById("importSourceTitle"),
    childTitleInput: document.getElementById("childTitleInput"),
    summaryInput: document.getElementById("summaryInput"),
    saveDraftButton: document.getElementById("saveDraftButton"),
    useImportedTargetButton: document.getElementById("useImportedTargetButton"),
    targetInput: document.getElementById("targetInput"),
    targetButton: document.getElementById("targetButton"),
    gpsButton: document.getElementById("gpsButton"),
    bleButton: document.getElementById("bleButton"),
    permissionsButton: document.getElementById("permissionsButton"),
    statusText: document.getElementById("statusText"),
    deviceValue: document.getElementById("deviceValue"),
  };

  function setStatus(message) {
    ui.statusText.textContent = message;
  }

  function render() {
    renderImportStatus();
    renderPendingResolution();
    renderPendingMissionDraft();

    if (state.location) {
      state.bearingDegrees = calculateBearing(state.location, state.target);
      const distance = calculateDistanceMeters(state.location, state.target);
      ui.distanceValue.textContent = `${Math.round(distance)} m`;
      ui.locationValue.textContent = `${state.location.latitude.toFixed(5)}, ${state.location.longitude.toFixed(5)}`;
      ui.bearingValue.textContent = `${Math.round(state.bearingDegrees)}°`;
    } else {
      ui.distanceValue.textContent = "--";
      ui.locationValue.textContent = "--";
      ui.bearingValue.textContent = "--";
    }

    if (typeof state.headingDegrees === "number") {
      ui.headingValue.textContent = `${Math.round(state.headingDegrees)}°`;
    } else {
      ui.headingValue.textContent = "--";
    }

    if (typeof state.headingDegrees === "number" && typeof state.bearingDegrees === "number") {
      state.arrowDegrees = calculateArrowDegrees(state.headingDegrees, state.bearingDegrees);
      ui.arrow.style.transform = `rotate(${state.arrowDegrees}deg)`;
    }

    ui.sourceValue.textContent = state.sourceLabel;
  }

  function renderImportStatus() {
    if (!state.importStatus && !state.shareDebug) {
      ui.importStatusBanner.hidden = true;
      return;
    }

    ui.importStatusBanner.hidden = false;
    ui.importStatusText.textContent = state.importStatus || "Share-Intent empfangen.";
    if (state.shareDebug) {
      ui.shareDebugText.hidden = false;
      ui.shareDebugText.textContent = state.shareDebug;
    } else {
      ui.shareDebugText.hidden = true;
    }
  }

  function renderPendingResolution() {
    if (!state.pendingResolution || state.pendingMissionDraft) {
      ui.resolutionPanel.hidden = true;
      return;
    }

    if (state.pendingResolution.status !== "NEEDS_ONLINE_RESOLUTION") {
      ui.resolutionPanel.hidden = true;
      return;
    }

    ui.resolutionPanel.hidden = false;
    ui.resolutionMessage.textContent = state.pendingResolution.messages;
    ui.resolutionCacheCode.textContent = state.pendingResolution.cacheCodeHint || "--";
    if (!ui.resolutionTitleInput.value && state.pendingResolution.cacheCodeHint) {
      ui.resolutionTitleInput.value = state.pendingResolution.cacheCodeHint;
    }
  }

  function renderPendingMissionDraft() {
    if (!state.pendingMissionDraft) {
      ui.importReviewPanel.hidden = true;
      return;
    }

    ui.importReviewPanel.hidden = false;
    ui.importCacheCode.textContent = state.pendingMissionDraft.cacheCode;
    ui.importSourceTitle.textContent = state.pendingMissionDraft.sourceTitle;
    ui.childTitleInput.value = state.pendingMissionDraft.childTitle;
    ui.summaryInput.value = state.pendingMissionDraft.summary;
  }

  function startBrowserGeolocation() {
    if (!navigator.geolocation) {
      setStatus("Browser-Geolocation ist auf diesem Geraet nicht verfuegbar.");
      return;
    }

    if (state.watchId !== null) {
      navigator.geolocation.clearWatch(state.watchId);
    }

    state.watchId = navigator.geolocation.watchPosition(
      (position) => {
        state.location = {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        };
        state.sourceLabel = state.hasNativeHost ? "Browser GPS + Native Sensoren" : "Browser GPS";
        setStatus("Browser-GPS aktiv.");
        render();
      },
      (error) => {
        setStatus(`Browser-GPS fehlgeschlagen: ${error.message}`);
      },
      {
        enableHighAccuracy: true,
        maximumAge: 2000,
        timeout: 10000,
      },
    );
  }

  function startBrowserOrientationFallback() {
    if (!window.addEventListener) {
      return;
    }

    window.addEventListener("deviceorientationabsolute", handleOrientation, true);
    window.addEventListener("deviceorientation", handleOrientation, true);
  }

  function handleOrientation(event) {
    const alpha = typeof event.webkitCompassHeading === "number"
      ? event.webkitCompassHeading
      : event.alpha;

    if (typeof alpha !== "number") {
      return;
    }

    if (!state.hasNativeHost) {
      state.headingDegrees = normalizeDegrees(alpha);
      state.sourceLabel = state.location ? "Browser GPS + Browser Kompass" : "Browser Kompass";
      render();
    }
  }

  function loadDeviceProfile() {
    if (!state.hasNativeHost) {
      ui.deviceValue.textContent = "Web Browser";
      return;
    }

    try {
      const profile = JSON.parse(window.AndroidHost.getDeviceProfile());
      ui.deviceValue.textContent = `Android ${profile.apiLevel}`;
      setStatus("Android-Host erkannt. Native Sensoren koennen optional zugeschaltet werden.");
    } catch (error) {
      ui.deviceValue.textContent = "Android Host";
      setStatus("Android-Host erkannt, Profil konnte aber nicht gelesen werden.");
    }
  }

  function loadPendingImportStatus() {
    if (!state.hasNativeHost || typeof window.AndroidHost.getPendingImportStatus !== "function") {
      return;
    }

    const summary = window.AndroidHost.getPendingImportStatus();
    if (summary) {
      state.importStatus = summary;
      setStatus(summary);
    }

    if (typeof window.AndroidHost.getPendingShareDebug === "function") {
      state.shareDebug = window.AndroidHost.getPendingShareDebug();
    }

    if (typeof window.AndroidHost.getPendingMissionDraftJson === "function") {
      const rawDraft = window.AndroidHost.getPendingMissionDraftJson();
      if (rawDraft) {
        state.pendingMissionDraft = JSON.parse(rawDraft);
        state.target = state.pendingMissionDraft.target;
        ui.targetInput.value = `${state.target.latitude},${state.target.longitude}`;
      }
    }

    if (typeof window.AndroidHost.getPendingResolutionJson === "function") {
      const rawResolution = window.AndroidHost.getPendingResolutionJson();
      if (rawResolution) {
        state.pendingResolution = JSON.parse(rawResolution);
      }
    }

    render();
  }

  function applyPendingImportPayload(payload) {
    if (payload.status) {
      state.importStatus = payload.status;
      setStatus(payload.status);
    }
    state.shareDebug = payload.shareDebug || null;

    state.pendingMissionDraft = payload.missionDraft || null;
    state.pendingResolution = payload.resolution || null;

    if (state.pendingMissionDraft?.target) {
      state.target = state.pendingMissionDraft.target;
      ui.targetInput.value = `${state.target.latitude},${state.target.longitude}`;
    }

    render();
  }

  function handleNativeEvent(rawEnvelope) {
    const envelope = typeof rawEnvelope === "string" ? JSON.parse(rawEnvelope) : rawEnvelope;
    const { type, payload } = envelope;

    if (type === "heading") {
      state.headingDegrees = payload.headingDegrees;
      state.sourceLabel = state.location ? "Native Sensor + GPS" : "Native Sensor";
      render();
      return;
    }

    if (type === "location") {
      state.location = {
        latitude: payload.latitude,
        longitude: payload.longitude,
      };
      state.sourceLabel = typeof state.headingDegrees === "number" ? "Native Sensor + GPS" : "Native GPS";
      render();
      return;
    }

    if (type === "ble-status") {
      setStatus(payload.status);
      return;
    }

    if (type === "native-status") {
      setStatus(payload.status);
      return;
    }

    if (type === "import-updated") {
      applyPendingImportPayload(payload);
    }
  }

  window.CacheKidNativeHost = {
    dispatch(rawEnvelope) {
      handleNativeEvent(rawEnvelope);
    },
    notifyPermissionsChanged() {
      setStatus("Native Berechtigungen wurden aktualisiert.");
      startBrowserGeolocation();
    },
  };

  ui.targetButton.addEventListener("click", () => {
    try {
      state.target = parseTarget(ui.targetInput.value);
      setStatus("Ziel aktualisiert.");
      render();
    } catch (error) {
      setStatus(error.message);
    }
  });

  ui.saveDraftButton.addEventListener("click", () => {
    if (!state.hasNativeHost || typeof window.AndroidHost.updatePendingMissionDraft !== "function") {
      setStatus("Draft-Update ist nur im Android-Host verfuegbar.");
      return;
    }

    const rawDraft = window.AndroidHost.updatePendingMissionDraft(
      ui.childTitleInput.value,
      ui.summaryInput.value,
    );

    if (rawDraft) {
      state.pendingMissionDraft = JSON.parse(rawDraft);
      setStatus("Missionstext gespeichert.");
      render();
    }
  });

  ui.resolveManuallyButton.addEventListener("click", () => {
    if (!state.hasNativeHost || typeof window.AndroidHost.resolvePendingCacheManually !== "function") {
      setStatus("Manuelle Cache-Aufloesung ist nur im Android-Host verfuegbar.");
      return;
    }

    const rawDraft = window.AndroidHost.resolvePendingCacheManually(
      ui.resolutionTitleInput.value,
      ui.resolutionTargetInput.value,
    );

    if (!rawDraft) {
      setStatus("Manuelle Aufloesung fehlgeschlagen. Bitte Titel und Koordinate pruefen.");
      return;
    }

    state.pendingMissionDraft = JSON.parse(rawDraft);
    state.pendingResolution = null;
    state.target = state.pendingMissionDraft.target;
    ui.targetInput.value = `${state.target.latitude},${state.target.longitude}`;
    setStatus("Cache manuell vervollstaendigt.");
    render();
  });

  ui.prefillImportedTargetButton.addEventListener("click", () => {
    ui.resolutionTargetInput.value = ui.targetInput.value;
    setStatus("Aktuelles Ziel in den Resolve-Schritt uebernommen.");
  });

  ui.useImportedTargetButton.addEventListener("click", () => {
    if (!state.pendingMissionDraft) {
      setStatus("Kein importierter Cache verfuegbar.");
      return;
    }

    state.target = state.pendingMissionDraft.target;
    ui.targetInput.value = `${state.target.latitude},${state.target.longitude}`;
    setStatus("Importiertes Ziel uebernommen.");
    render();
  });

  ui.gpsButton.addEventListener("click", () => {
    startBrowserGeolocation();
  });

  ui.permissionsButton.addEventListener("click", () => {
    if (state.hasNativeHost) {
      window.AndroidHost.requestNativePermissions();
    } else {
      setStatus("Kein Android-Host vorhanden. Browser fragt Berechtigungen selbst an.");
      startBrowserGeolocation();
    }
  });

  ui.bleButton.addEventListener("click", () => {
    if (state.hasNativeHost) {
      window.AndroidHost.connectBleSensor();
    } else {
      setStatus("BLE ist im reinen Browser-Modus nicht eingebunden.");
    }
  });

  loadDeviceProfile();
  loadPendingImportStatus();
  startBrowserOrientationFallback();
  startBrowserGeolocation();

  if (state.hasNativeHost) {
    window.AndroidHost.startNativeSensors();
  }

  render();
}());
