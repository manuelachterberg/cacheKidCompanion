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
  };

  const ui = {
    arrow: document.getElementById("arrow"),
    distanceValue: document.getElementById("distanceValue"),
    headingValue: document.getElementById("headingValue"),
    bearingValue: document.getElementById("bearingValue"),
    sourceValue: document.getElementById("sourceValue"),
    locationValue: document.getElementById("locationValue"),
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
  startBrowserOrientationFallback();
  startBrowserGeolocation();

  if (state.hasNativeHost) {
    window.AndroidHost.startNativeSensors();
  }

  render();
}());
