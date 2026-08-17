export function assetUrl(asset: string | { src: string }): string {
  return typeof asset === "string" ? asset : asset.src;
}
