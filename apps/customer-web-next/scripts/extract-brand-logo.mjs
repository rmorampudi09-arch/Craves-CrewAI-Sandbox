import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";

const appRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const sourceDirectory = resolve(appRoot, "scripts/assets");
const targetPath = resolve(
  appRoot,
  "public/brand/craves-logo-20260805.png",
);
const expectedSha256 = "afb6751bb1291f5cba13f3223140cc42229cb00696e025f617766527d6c7fd07";
const sourceParts = [
  ["craves-logo-20260805.base64.00", "9259a9a37d402e0a92fdc8a72b5bf02eae108096adb4fd281f61686a0a515751"],
  ["craves-logo-20260805.base64.01", "4d47f211610aae0c973036916bfde4406543c46a8bd3185a68e3c123764b0d07"],
  ["craves-logo-20260805.base64.02", "44cbc975b44c6a2107f205988aca9db13dd6ec81a40e828bb9f9982c11e97f01"],
  ["craves-logo-20260805.base64.03", "aa3f3616553ec8147d9710780535bc4e29bd4ea4c5e5617c4aef439e178a98f3"],
  ["craves-logo-20260805.base64.04a", "691e1a5037296f1f585fa2f82dc79afd2f5894953de3692e9aead8c26574987b"],
  ["craves-logo-20260805.base64.04b", "8bce75599861c9dcfd9582498f8ea95c6e13fc4addc582a10dfe1d4532e63a0b"],
];

const encodedParts = [];
for (const [name, expectedPartSha256] of sourceParts) {
  const encoded = (await readFile(resolve(sourceDirectory, name), "utf8")).trim();
  const actualPartSha256 = createHash("sha256").update(encoded).digest("hex");
  if (actualPartSha256 !== expectedPartSha256) {
    throw new Error(
      `Canonical Craves logo source part ${name} is incomplete: ${actualPartSha256}`,
    );
  }
  encodedParts.push(encoded);
}

const png = Buffer.from(encodedParts.join(""), "base64");
const signature = png.subarray(0, 8).toString("hex");
const sha256 = createHash("sha256").update(png).digest("hex");
if (signature !== "89504e470d0a1a0a" || sha256 !== expectedSha256) {
  throw new Error(`Canonical Craves logo source has unexpected hash ${sha256}`);
}

const decoded = await sharp(png)
  .ensureAlpha()
  .raw()
  .toBuffer({ resolveWithObject: true });
const { width, height, channels } = decoded.info;
if (width !== 112 || height !== 112 || channels !== 4) {
  throw new Error("Canonical Craves logo is not the approved 112x112 RGBA image");
}

await mkdir(dirname(targetPath), { recursive: true });
await writeFile(targetPath, png);
console.log(
  `Prepared approved Craves logo: ${width}x${height}, ${png.length} bytes, ${sha256}`,
);
