import test from "node:test";
import assert from "node:assert/strict";
import {
  calculateArrowDegrees,
  calculateBearing,
  calculateDistanceMeters,
  normalizeDegrees,
  parseTarget,
} from "../app/src/main/assets/web/navigation.js";

test("parseTarget parses latitude and longitude", () => {
  assert.deepEqual(parseTarget("52.520008, 13.404954"), {
    latitude: 52.520008,
    longitude: 13.404954,
  });
});

test("parseTarget rejects invalid input", () => {
  assert.throws(() => parseTarget("invalid"), /Breite,Laenge/);
});

test("normalizeDegrees keeps values in 0-359 range", () => {
  assert.equal(normalizeDegrees(-90), 270);
  assert.equal(normalizeDegrees(450), 90);
});

test("calculateBearing returns east for due-east target", () => {
  const bearing = calculateBearing(
    { latitude: 52.0, longitude: 13.0 },
    { latitude: 52.0, longitude: 14.0 },
  );

  assert.ok(bearing > 89 && bearing < 91);
});

test("calculateDistanceMeters is zero for identical points", () => {
  assert.equal(
    Math.round(calculateDistanceMeters(
      { latitude: 52.0, longitude: 13.0 },
      { latitude: 52.0, longitude: 13.0 },
    )),
    0,
  );
});

test("calculateArrowDegrees normalizes relative direction", () => {
  assert.equal(calculateArrowDegrees(350, 10), 20);
  assert.equal(calculateArrowDegrees(10, 350), 340);
});
