(function () {
  const {
    calculateArrowDegrees,
    calculateBearing,
    calculateDistanceMeters,
    normalizeDegrees,
    parseTarget,
  } = window.CacheKidNavigation;

  const state = {
    target: { latitude: 52.520008, longitude: 13.404954 },
    headingDegrees: null,
    mapBearingDegrees: null,
    location: null,
    bearingDegrees: null,
    sourceLabel: "Browser",
    watchId: null,
    hasNativeHost: typeof window.AndroidHost !== "undefined",
    pendingMissionDraft: null,
    pendingResolution: null,
    activeMission: null,
    missionRuntime: {
      missionId: null,
      startDistanceMeters: null,
    },
    importStatus: null,
    shareDebug: null,
  };

  const ui = {
    kidMissionView: document.getElementById("kidMissionView"),
    kidDistanceValue: document.getElementById("kidDistanceValue"),
    kidDistanceScaleFill: document.getElementById("kidDistanceScaleFill"),
    kidHeadingDebug: document.getElementById("kidHeadingDebug"),
    kidCompassDialRotor: document.getElementById("kidCompassDialRotor"),
    kidCompassDial: document.getElementById("kidCompassDial"),
    kidCompassNeedle: document.getElementById("kidCompassNeedle"),
    kidMapTerrain: document.getElementById("kidMapTerrain"),
    kidMapRouteGlow: document.getElementById("kidMapRouteGlow"),
    kidMapRoute: document.getElementById("kidMapRoute"),
    kidMapTargetMarker: document.getElementById("kidMapTargetMarker"),
    appEyebrow: document.getElementById("appEyebrow"),
    appTitle: document.getElementById("appTitle"),
    missionOverviewCard: document.getElementById("missionOverviewCard"),
    missionTitle: document.getElementById("missionTitle"),
    missionSummary: document.getElementById("missionSummary"),
    missionCacheCode: document.getElementById("missionCacheCode"),
    missionTargetLabel: document.getElementById("missionTargetLabel"),
    missionHint: document.getElementById("missionHint"),
    distanceValue: document.getElementById("distanceValue"),
    headingValue: document.getElementById("headingValue"),
    bearingValue: document.getElementById("bearingValue"),
    sourceValue: document.getElementById("sourceValue"),
    headingCard: document.getElementById("headingCard"),
    bearingCard: document.getElementById("bearingCard"),
    sourceCard: document.getElementById("sourceCard"),
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
    renderActiveMission();
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

    ui.sourceValue.textContent = state.sourceLabel;
    renderKidMissionView();
  }

  function renderActiveMission() {
    const hasActiveMission = Boolean(state.activeMission);
    document.body.classList.toggle("kid-mode", hasActiveMission && state.hasNativeHost);
    document.body.classList.toggle("native-map-mode", hasActiveMission && state.hasNativeHost);

    if (!hasActiveMission) {
      ui.missionOverviewCard.hidden = true;
      ui.appEyebrow.textContent = "Hybrid Navigator";
      ui.appTitle.textContent = "CacheKid Companion";
      return;
    }

    ui.missionOverviewCard.hidden = false;
    ui.missionTitle.textContent = state.activeMission.childTitle;
    ui.missionSummary.textContent = state.activeMission.summary;
    ui.missionCacheCode.textContent = state.activeMission.cacheCode;
    ui.missionTargetLabel.textContent = `${state.activeMission.target.latitude.toFixed(5)}, ${state.activeMission.target.longitude.toFixed(5)}`;
    ui.missionHint.textContent = "Folge dem Pfeil. Wenn die Distanz kleiner wird, kommst du naeher an den Schatz.";
    ui.appEyebrow.textContent = "Schatzkarte";
    ui.appTitle.textContent = state.activeMission.childTitle;
  }

  function renderKidMissionView() {
    const hasKidMission = Boolean(state.activeMission && state.hasNativeHost);
    ui.kidMissionView.hidden = !hasKidMission;
    if (!hasKidMission) {
      return;
    }

    if (state.missionRuntime.missionId !== state.activeMission.missionId) {
      state.missionRuntime = {
        missionId: state.activeMission.missionId,
        startDistanceMeters: null,
      };
    }

    let distanceMeters = null;
    if (state.location) {
      distanceMeters = calculateDistanceMeters(state.location, state.target);
      if (state.missionRuntime.startDistanceMeters == null) {
        state.missionRuntime.startDistanceMeters = distanceMeters;
      }
    }

    renderKidCompass(distanceMeters);
    renderKidMap(distanceMeters);
  }

  function renderKidCompass(distanceMeters) {
    if (typeof distanceMeters === "number") {
      ui.kidDistanceValue.textContent = `${Math.round(distanceMeters)} m`;
      const startDistance = state.missionRuntime.startDistanceMeters || distanceMeters;
      const progress = startDistance > 0
        ? Math.max(0, Math.min(1, 1 - (distanceMeters / startDistance)))
        : 0;
      ui.kidDistanceScaleFill.style.width = `${Math.max(6, Math.round(progress * 100))}%`;
    } else {
      ui.kidDistanceValue.textContent = "--";
      ui.kidDistanceScaleFill.style.width = "0%";
    }

    const dialBearing = typeof state.mapBearingDegrees === "number"
      ? state.mapBearingDegrees
      : state.headingDegrees;
    const hasStableHeading = typeof dialBearing === "number" && typeof state.bearingDegrees === "number";
    ui.kidCompassNeedle.hidden = !hasStableHeading;

    if (typeof dialBearing === "number") {
      ui.kidCompassDialRotor.style.transform = `rotate(${-dialBearing}deg) scale(1.02)`;
      ui.kidCompassDialRotor.style.setProperty("--dial-bearing", `${dialBearing}deg`);
    } else {
      ui.kidCompassDialRotor.style.transform = "rotate(0deg) scale(1.02)";
      ui.kidCompassDialRotor.style.setProperty("--dial-bearing", "0deg");
    }

    if (hasStableHeading) {
      const arrowDegrees = calculateArrowDegrees(dialBearing, state.bearingDegrees);
      ui.kidCompassNeedle.style.transform = `rotate(${arrowDegrees}deg)`;
    }
  }

  function renderKidMap(distanceMeters) {
    const relativeAngle = typeof state.headingDegrees === "number" && typeof state.bearingDegrees === "number"
      ? calculateArrowDegrees(state.headingDegrees, state.bearingDegrees)
      : typeof state.bearingDegrees === "number"
        ? state.bearingDegrees
        : 0;

    const playerX = 50;
    const playerY = 104;
    const routeReach = distanceMeters == null
      ? 38
      : Math.max(24, Math.min(54, 26 + Math.log10(distanceMeters + 10) * 10));
    const angleRadians = (relativeAngle * Math.PI) / 180;
    const targetX = clamp(playerX + Math.sin(angleRadians) * (routeReach * 0.72), 18, 82);
    const targetY = clamp(playerY - Math.cos(angleRadians) * routeReach, 22, 116);
    const bendX = playerX + Math.sin(angleRadians) * 8 + Math.cos(angleRadians) * 7;
    const bendY = playerY - routeReach * 0.48;

    const routePath = [
      `M ${playerX} ${playerY}`,
      `Q ${bendX.toFixed(2)} ${bendY.toFixed(2)} ${targetX.toFixed(2)} ${targetY.toFixed(2)}`,
    ].join(" ");

    ui.kidMapRouteGlow.setAttribute("d", routePath);
    ui.kidMapRoute.setAttribute("d", routePath);

    const targetScale = distanceMeters == null
      ? 1
      : clamp(1 + ((180 - Math.min(distanceMeters, 180)) / 150), 1, 2.15);
    ui.kidMapTargetMarker.setAttribute(
      "transform",
      `translate(${targetX.toFixed(2)} ${targetY.toFixed(2)}) scale(${targetScale.toFixed(2)})`,
    );

    renderKidTerrain(relativeAngle, targetX, targetY);
  }

  function renderKidTerrain(relativeAngle, targetX, targetY) {
    if (state.activeMission && state.hasNativeHost) {
      ui.kidMapTerrain.innerHTML = "";
      return;
    }
    if (state.activeMission?.baseMap?.svgContent) {
      ui.kidMapTerrain.innerHTML = state.activeMission.baseMap.svgContent;
      return;
    }
    if (state.activeMission?.offlineMap?.svgContent) {
      ui.kidMapTerrain.innerHTML = state.activeMission.offlineMap.svgContent;
      return;
    }
    ui.kidMapTerrain.innerHTML = "";
  }

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function renderImportStatus() {
    if (state.hasNativeHost) {
      ui.importStatusBanner.hidden = true;
      return;
    }

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
    if (state.hasNativeHost) {
      ui.resolutionPanel.hidden = true;
      return;
    }

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
    if (state.hasNativeHost) {
      ui.importReviewPanel.hidden = true;
      return;
    }

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

    if (typeof window.AndroidHost.getActiveMissionJson === "function") {
      const rawMission = window.AndroidHost.getActiveMissionJson();
      if (rawMission) {
        state.activeMission = JSON.parse(rawMission);
        state.target = state.activeMission.target;
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

    if (type === "map-orientation") {
      state.mapBearingDegrees =
        typeof payload.mapBearingDegrees === "number" ? payload.mapBearingDegrees : null;
      if (typeof payload.targetBearingDegrees === "number") {
        state.bearingDegrees = payload.targetBearingDegrees;
      }
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
      return;
    }

    if (type === "active-mission-updated") {
      state.activeMission = payload.activeMission || null;
      state.missionRuntime = {
        missionId: state.activeMission?.missionId || null,
        startDistanceMeters: null,
      };
      if (state.activeMission?.target) {
        state.target = state.activeMission.target;
        ui.targetInput.value = `${state.target.latitude},${state.target.longitude}`;
      }
      render();
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
      ui.targetInput.value,
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
