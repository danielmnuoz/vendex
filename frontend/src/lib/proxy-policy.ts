const allowedRoots = new Set([
  "profile",
  "cards",
  "sets",
  "inventory",
  "buylist",
  "events",
  "overlaps",
  "notifications",
]);

export function validProxyPath(segments: string[]) {
  return segments.length > 0
    && allowedRoots.has(segments[0])
    && segments.every((segment) => /^[A-Za-z0-9_-]+$/.test(segment));
}
