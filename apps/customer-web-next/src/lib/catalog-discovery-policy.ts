export const DEFAULT_DISCOVERY_RADIUS_METERS = 5_000;
export const MAX_DISCOVERY_RADIUS_METERS = 50_000;

const EXPANSION_RADII_METERS = [15_000, MAX_DISCOVERY_RADIUS_METERS] as const;

export function candidateDiscoveryRadii(
  requestedRadiusMeters = DEFAULT_DISCOVERY_RADIUS_METERS,
): number[] {
  if (
    !Number.isInteger(requestedRadiusMeters) ||
    requestedRadiusMeters < 1 ||
    requestedRadiusMeters > MAX_DISCOVERY_RADIUS_METERS
  ) {
    throw new Error(
      "Discovery radius must be a whole number between 1 and 50000 metres.",
    );
  }

  return [requestedRadiusMeters, ...EXPANSION_RADII_METERS]
    .filter(
      (radius, index, values) =>
        radius >= requestedRadiusMeters && values.indexOf(radius) === index,
    )
    .sort((left, right) => left - right);
}

export function formatDiscoveryRadius(radiusMeters: number): string {
  if (radiusMeters < 1_000) return `${radiusMeters} m`;
  const kilometres = radiusMeters / 1_000;
  return `${Number.isInteger(kilometres) ? kilometres : kilometres.toFixed(1)} km`;
}
