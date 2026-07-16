/**
 * 生成 TabBar 占位图标（PNG）
 * 使用 Node.js Canvas 生成简单的占位图标
 *
 * 实际项目开发时请替换为设计师提供的图标
 */

const fs = require('fs')
const path = require('path')

// 用纯 PNG 格式创建最小有效 PNG（1x1 像素透明PNG的base64）
// 微信小程序 tabBar 图标建议 81x81px
// 这里创建简单的占位 PNG 文件

// 最小有效 PNG 文件（透明 81x81）
function createMinimalPng() {
  // PNG header + IHDR + IDAT + IEND
  const width = 81
  const height = 81

  // 构建一个简单的 PNG 二进制数据
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])

  // IHDR chunk
  const ihdrData = Buffer.alloc(13)
  ihdrData.writeUInt32BE(width, 0)
  ihdrData.writeUInt32BE(height, 4)
  ihdrData[8] = 8  // bit depth
  ihdrData[9] = 6  // color type (RGBA)
  ihdrData[10] = 0 // compression
  ihdrData[11] = 0 // filter
  ihdrData[12] = 0 // interlace

  const ihdr = createChunk('IHDR', ihdrData)

  // 创建图像数据（RGBA）
  const rawRow = width * 4 + 1 // +1 for filter byte
  const rawData = Buffer.alloc(height * rawRow)

  // 每行设置 filter = 0 (None)
  for (let y = 0; y < height; y++) {
    rawData[y * rawRow] = 0 // filter
    for (let x = 0; x < width; x++) {
      const offset = y * rawRow + 1 + x * 4
      rawData[offset] = 100      // R
      rawData[offset + 1] = 100  // G
      rawData[offset + 2] = 100  // B
      rawData[offset + 3] = 200  // A (semi-transparent)
    }
  }

  // 简单的 zlib 压缩（不压缩，store）
  // 实际 PNG 需要 zlib，这里用 Node.js zlib
  const zlib = require('zlib')
  const compressed = zlib.deflateSync(rawData)

  const idat = createChunk('IDAT', compressed)
  const iend = createChunk('IEND', Buffer.alloc(0))

  return Buffer.concat([signature, ihdr, idat, iend])
}

function createChunk(type, data) {
  const length = Buffer.alloc(4)
  length.writeUInt32BE(data.length)

  const typeBuffer = Buffer.from(type, 'ascii')
  const crc32 = crc(typeBuffer, data)
  const crcBuf = Buffer.alloc(4)
  crcBuf.writeUInt32BE(crc32 >>> 0)

  return Buffer.concat([length, typeBuffer, data, crcBuf])
}

function crc(buf, data) {
  const combined = Buffer.concat([buf, data])
  let crc = 0xFFFFFFFF
  for (let i = 0; i < combined.length; i++) {
    crc ^= combined[i]
    for (let j = 0; j < 8; j++) {
      crc = (crc >>> 1) ^ ((crc & 1) ? 0xEDB88320 : 0)
    }
  }
  return ~crc
}

// 生成图标
const icons = ['home', 'scores', 'ability', 'training', 'profile']
const dir = path.join(__dirname, '..', 'frontend', 'src', 'static', 'tabbar')

if (!fs.existsSync(dir)) {
  fs.mkdirSync(dir, { recursive: true })
}

icons.forEach(name => {
  const png = createMinimalPng()
  fs.writeFileSync(path.join(dir, `${name}.png`), png)
  console.log(`Created ${name}.png`)
})

console.log('All tabbar icons generated!')
