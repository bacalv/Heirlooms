import { copyFileSync } from 'fs'
import { join, dirname } from 'path'
import { fileURLToPath } from 'url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const src  = join(root, 'node_modules', '@ffmpeg', 'core', 'dist', 'umd')
const dst  = join(root, 'public')

for (const f of ['ffmpeg-core.js', 'ffmpeg-core.wasm']) {
  copyFileSync(join(src, f), join(dst, f))
}
console.log('ffmpeg-core files copied to public/')
