// Export real device captures without stretching, cropping, or changing gameplay.
// Run: node tools/prepare-store-screenshots.cjs [path-to-node_modules]
const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require(require.resolve('sharp', { paths: [process.argv[2] || process.cwd()] }));
const root = path.resolve(__dirname, '..');

async function main() {
  const source = path.join(root, 'store-assets/raw/android-tr');
  const target = path.join(root, 'store-assets/play/android-tr');
  await fs.mkdir(target, { recursive: true });
  const manifest = [];
  for (const scene of ['board', 'tutorial', 'result', 'menu', 'settings']) {
    const input = path.join(source, `${scene}.png`);
    const output = path.join(target, `${scene}-1080x1920.png`);
    const original = await sharp(input).metadata();
    await sharp(input).resize(1080, 1920, {
      fit: 'contain', background: '#394347', kernel: 'lanczos3',
    }).flatten({ background: '#394347' }).png().toFile(output);
    const check = await sharp(output).metadata();
    const stat = await fs.stat(output);
    if (check.width !== 1080 || check.height !== 1920 || check.hasAlpha || stat.size > 8 * 1024 * 1024) {
      throw new Error(`Invalid store image: ${output}`);
    }
    manifest.push({ scene, source: `store-assets/raw/android-tr/${scene}.png`,
      sourceSize: [original.width, original.height], output: path.basename(output),
      size: [check.width, check.height], bytes: stat.size,
      method: 'Proportional resize with anthracite padding; complete real capture preserved' });
  }
  await fs.writeFile(path.join(target, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log(JSON.stringify(manifest, null, 2));
}
main().catch(error => { console.error(error); process.exitCode = 1; });
