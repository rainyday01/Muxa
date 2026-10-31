import AppKit
import Foundation

let size = 1024.0
let scale = size / 108.0
guard let bitmap = NSBitmapImageRep(
    bitmapDataPlanes: nil,
    pixelsWide: Int(size),
    pixelsHigh: Int(size),
    bitsPerSample: 8,
    samplesPerPixel: 4,
    hasAlpha: true,
    isPlanar: false,
    colorSpaceName: .deviceRGB,
    bytesPerRow: 0,
    bitsPerPixel: 0
) else {
    fputs("failed to allocate bitmap\n", stderr)
    exit(1)
}
bitmap.size = NSSize(width: size, height: size)
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)

func p(_ x: Double, _ y: Double) -> NSPoint {
    NSPoint(x: x * scale, y: (108 - y) * scale)
}

let plate = NSGradient(colors: [
    NSColor(srgbRed: 0x16 / 255, green: 0x15 / 255, blue: 0x1A / 255, alpha: 1),
    NSColor(srgbRed: 0x0A / 255, green: 0x09 / 255, blue: 0x0C / 255, alpha: 1)
])!
plate.draw(
    from: p(18, 8),
    to: p(96, 100),
    options: [.drawsBeforeStartingLocation, .drawsAfterEndingLocation]
)

// Android adaptive crop is ~66% of the 108 canvas. iOS shows the full
// square, so the glyph is 0.42 / 0.66 ≈ 0.64 of the design hex.
let glyphScale = 0.64
let glowCenter = p(54, 52)
let glowRadius = 46 * scale
let glow = NSGradient(colorsAndLocations:
    (NSColor(srgbRed: 0xE1 / 255, green: 0x62 / 255, blue: 0x59 / 255, alpha: 0.40), 0),
    (NSColor(srgbRed: 0xE1 / 255, green: 0x62 / 255, blue: 0x59 / 255, alpha: 0.20), 0.35),
    (NSColor(srgbRed: 0xE1 / 255, green: 0x62 / 255, blue: 0x59 / 255, alpha: 0.07), 0.70),
    (NSColor(srgbRed: 0xE1 / 255, green: 0x62 / 255, blue: 0x59 / 255, alpha: 0), 1)
)!
glow.draw(
    in: NSBezierPath(ovalIn: NSRect(
        x: glowCenter.x - glowRadius,
        y: glowCenter.y - glowRadius,
        width: glowRadius * 2,
        height: glowRadius * 2
    )),
    relativeCenterPosition: .zero
)

func mapped(_ x: Double, _ y: Double) -> NSPoint {
    let nx = 54 + (x - 54) * glyphScale
    let ny = 53 + (y - 54) * glyphScale
    return p(nx, ny)
}

func stroke(_ points: [NSPoint], color: NSColor, width: Double, closed: Bool) {
    let path = NSBezierPath()
    path.move(to: points[0])
    for point in points.dropFirst() {
        path.line(to: point)
    }
    if closed { path.close() }
    path.lineJoinStyle = .round
    path.lineCapStyle = .round
    path.lineWidth = width * glyphScale * scale
    color.setStroke()
    path.stroke()
}

let hex = [
    mapped(54, 12), mapped(90, 32), mapped(90, 76),
    mapped(54, 96), mapped(18, 76), mapped(18, 32)
]
stroke(hex, color: NSColor(srgbRed: 0xF7 / 255, green: 0xF6 / 255, blue: 0xF3 / 255, alpha: 0.94), width: 3.2, closed: true)
stroke(hex, color: NSColor(srgbRed: 0xE1 / 255, green: 0x62 / 255, blue: 0x59 / 255, alpha: 0.33), width: 1.4, closed: true)

let paper = NSColor(srgbRed: 0xF7 / 255, green: 0xF6 / 255, blue: 0xF3 / 255, alpha: 1)
stroke([mapped(40, 48), mapped(50, 58), mapped(40, 68)], color: paper, width: 5, closed: false)
stroke([mapped(52, 68), mapped(68, 68)], color: paper, width: 5, closed: false)

NSGraphicsContext.restoreGraphicsState()

guard let png = bitmap.representation(using: .png, properties: [:]) else {
    fputs("failed to encode PNG\n", stderr)
    exit(1)
}

let output = CommandLine.arguments.dropFirst().first
    ?? "Muxa/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
try png.write(to: URL(fileURLWithPath: output))
print(output)
