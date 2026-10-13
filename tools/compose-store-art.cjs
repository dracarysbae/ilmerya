// AI supplies the material background; gameplay remains a real device capture.
// Run: node tools/compose-store-art.cjs [path-to-node_modules]
const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require(require.resolve('sharp', { paths: [process.argv[2] || process.cwd()] }));
const root = path.resolve(__dirname, '..');
const assets = path.join(root, 'store-assets/play');
const escape = text => text.replace(/[&<>]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));
const scenes = [
  ['board', 'Doğal taşlar. Derin strateji.', 'Ceviz tablada kendi zincirini kur.'],
  ['tutorial', 'Üç taşı buluştur.', 'Birleştir, yükselt, yeni hamleni planla.'],
  ['menu', 'Kendi ritmini seç.', 'Dingin veya Akış. İki farklı oyun deneyimi.'],
  ['result', 'Her zincir yeni bir adım.', 'Skorunu yükselt, bir sonraki oyuna hazırlan.'],
];
async function main() {
  const outputDir = path.join(assets, 'screenshots-tr');
  await fs.mkdir(outputDir, { recursive: true });
  await sharp(path.join(assets, 'ai/icon-source.png')).resize(512, 512).flatten().png()
    .toFile(path.join(assets, 'ai/ilmerya-play-icon-ai-512.png'));
  const manifest = [];
  for (let index = 0; index < scenes.length; index++) {
    const [scene, title, subtitle] = scenes[index];
    const input = path.join(root, `store-assets/raw/android-tr/${scene}.png`);
    // Exclude Android's status/navigation bars only. No game pixels are repainted.
    const capture = await sharp(input).extract({ left: 0, top: 96, width: 1080, height: 2196 })
      .resize({ width: 804 }).png().toBuffer();
    const size = await sharp(capture).metadata();
    const x = Math.round((1080 - size.width) / 2), y = 260;
    const text = Buffer.from(`<svg width="1080" height="1920" xmlns="http://www.w3.org/2000/svg">
      <text x="540" y="72" text-anchor="middle" fill="#c7ae87" font-family="Segoe UI" font-size="24" letter-spacing="8">İLMERYA</text>
      <text x="540" y="148" text-anchor="middle" fill="#f4eedf" font-family="Segoe UI" font-weight="600" font-size="52">${escape(title)}</text>
      <text x="540" y="202" text-anchor="middle" fill="#b6c8c2" font-family="Segoe UI" font-size="30">${escape(subtitle)}</text>
      <rect x="${x - 4}" y="${y - 4}" width="${size.width + 8}" height="${size.height + 8}" rx="2" fill="#aa8d63"/>
    </svg>`);
    const file = `${String(index + 1).padStart(2, '0')}-${scene}-1080x1920.png`;
    const output = path.join(outputDir, file);
    await sharp(path.join(assets, 'ai/screenshot-background.png')).resize(1080, 1920)
      .composite([{ input: text }, { input: capture, left: x, top: y }]).removeAlpha().png().toFile(output);
    const check = await sharp(output).metadata();
    const stat = await fs.stat(output);
    if (check.width !== 1080 || check.height !== 1920 || check.hasAlpha || stat.size >= 8 * 1024 * 1024)
      throw new Error(`Invalid screenshot: ${file}`);
    manifest.push({ file, scene, title, subtitle, width: 1080, height: 1920, bytes: stat.size,
      gameplaySource: `store-assets/raw/android-tr/${scene}.png`,
      processing: 'Android status/navigation bars excluded; remaining capture proportionally resized, never redrawn by AI',
      background: 'AI-generated walnut, slate and mineral presentation' });
  }
  await fs.writeFile(path.join(outputDir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log(JSON.stringify(manifest, null, 2));
}
main().catch(error => { console.error(error); process.exitCode = 1; });
