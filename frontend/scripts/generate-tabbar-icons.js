/**
 * 补充生成 active 状态的 TabBar 图标
 */
const fs = require('fs')
const path = require('path')
const zlib = require('zlib')

function createMinimalPng(r, g, b, a) {
  const width = 81, height = 81
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])
  const ihdrData = Buffer.alloc(13)
  ihdrData.writeUInt32BE(width, 0)
  ihdrData.writeUInt32BE(height, 4)
  ihdrData[8] = 8; ihdrData[9] = 6
  const ihdr = createChunk('IHDR', ihdrData)
  const rawRow = width * 4 + 1
  const rawData = Buffer.alloc(height * rawRow)
  for (let y = 0; y < height; y++) {
    rawData[y * rawRow] = 0
    for (let x = 0; x < width; x++) {
      const offset = y * rawRow + 1 + x * 4
      rawData[offset] = r; rawData[offset + 1] = g
      rawData[offset + 2] = b; rawData[offset + 3] = a
    }
  }
  const compressed = zlib.deflateSync(rawData)
  const idat = createChunk('IDAT', compressed)
  const iend = createChunk('IEND', Buffer.alloc(0))
  return Buffer.concat([signature, ihdr, idat, iend])
}

function createChunk(type, data) {
  const length = Buffer.alloc(4); length.writeUInt32BE(data.length)
  const typeBuffer = Buffer.from(type, 'ascii')
  const crc32 = crc(typeBuffer, data)
  const crcBuf = Buffer.alloc(4); crcBuf.writeUInt32BE(crc32 >>> 0)
  return Buffer.concat([length, typeBuffer, data, crcBuf])
}

function crc(buf, data) {
  const combined = Buffer.concat([buf, data])
  let c = 0xFFFFFFFF
  for (let i = 0; i < combined.length; i++) {
    c ^= combined[i]
    for (let j = 0; j < 8; j++) c = (c >>> 1) ^ ((c & 1) ? 0xEDB88320 : 0)
  }
  return ~c
}

const dir = path.join(__dirname, '..', 'src', 'static', 'tabbar')
const icons = [
  { name: 'home', active: 'home-active' },
  { name: 'radar', active: 'radar-active' },
  { name: 'music', active: 'music-active' },
  { name: 'training', active: 'training-active' },
  { name: 'user', active: 'user-active' },
]

icons.forEach(({ name, active }) => {
  // Normal: 灰色半透明
  fs.writeFileSync(path.join(dir, `${name}.png`), createMinimalPng(153, 153, 153, 200))
  // Active: 橙色（#FF6B35）不透明
  fs.writeFileSync(path.join(dir, `${active}.png`), createMinimalPng(255, 107, 53, 255))
  console.log(`Created ${name}.png and ${active}.png`)
})

console.log('All tabbar icons (normal + active) generated!')
