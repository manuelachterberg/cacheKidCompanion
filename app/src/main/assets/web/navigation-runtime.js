(function () {
  function parseTarget(rawValue) {
    const parts = rawValue.split(",").map((part) => Number(part.trim()));
    if (parts.length !== 2 || parts.some((part) => Number.isNaN(part))) {
      throw new Error("Bitte Format 'Breite,Laenge' verwenden.");
    }
    return { latitude: parts[0], longitude: parts[1] };
  }

  function toRadians(value) {
    return (value * Math.PI) / 180;
  }

  function toDegrees(value) {
    return (value * 180) / Math.PI;
  }

  function normalizeDegrees(degrees) {
    return ((degrees % 360) + 360) % 360;
  }

  function calculateBearing(from, to) {
    const lat1 = toRadians(from.latitude);
    const lon1 = toRadians(from.longitude);
    const lat2 = toRadians(to.latitude);
    const lon2 = toRadians(to.longitude);
    const y = Math.sin(lon2 - lon1) * Math.cos(lat2);
    const x = Math.cos(lat1) * Math.sin(lat2) -
      Math.sin(lat1) * Math.cos(lat2) * Math.cos(lon2 - lon1);
    return normalizeDegrees(toDegrees(Math.atan2(y, x)));
  }

  function calculateDistanceMeters(from, to) {
    const earthRadius = 6371000;
    const dLat = toRadians(to.latitude - from.latitude);
    const dLon = toRadians(to.longitude - from.longitude);
    const lat1 = toRadians(from.latitude);
    const lat2 = toRadians(to.latitude);
    const a = Math.sin(dLat / 2) ** 2 +
      Math.sin(dLon / 2) ** 2 * Math.cos(lat1) * Math.cos(lat2);
    return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  function calculateArrowDegrees(headingDegrees, bearingDegrees) {
    return normalizeDegrees(bearingDegrees - headingDegrees);
  }

  window.CacheKidNavigation = {
    calculateArrowDegrees,
    calculateBearing,
    calculateDistanceMeters,
    normalizeDegrees,
    parseTarget,
  };
}());
