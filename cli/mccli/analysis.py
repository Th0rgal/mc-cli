"""
Screenshot Analysis

Extract quantitative metrics from screenshots for LLM feedback.
Uses a minimal PNG reader with no external dependencies.
"""

from __future__ import annotations

import struct
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import Optional


@dataclass
class ImageMetrics:
    """Quantitative metrics extracted from a screenshot."""

    # Brightness
    brightness_mean: float      # 0-255
    brightness_std: float       # Standard deviation
    brightness_min: int
    brightness_max: int

    # Contrast
    contrast_ratio: float       # Max/min ratio

    # Color
    color_temp: float          # 0=warm (red), 1=cool (blue)
    saturation_mean: float     # 0-1

    # Distribution
    histogram: list[int]       # 16-bin brightness histogram

    # Metadata
    width: int
    height: int
    path: str

    def to_dict(self) -> dict:
        """Convert to dictionary for JSON output."""
        return {
            "brightness": {
                "mean": round(self.brightness_mean, 2),
                "std": round(self.brightness_std, 2),
                "min": self.brightness_min,
                "max": self.brightness_max,
            },
            "contrast_ratio": round(self.contrast_ratio, 2),
            "color_temp": round(self.color_temp, 3),
            "saturation_mean": round(self.saturation_mean, 3),
            "histogram": self.histogram,
            "dimensions": {"width": self.width, "height": self.height},
            "path": self.path,
        }

    def summary(self) -> str:
        """Human-readable summary."""
        lines = [
            f"Image: {self.width}x{self.height}",
            f"Brightness: {self.brightness_mean:.1f} (std: {self.brightness_std:.1f})",
            f"Range: {self.brightness_min}-{self.brightness_max} (contrast: {self.contrast_ratio:.1f}x)",
            f"Color temp: {'warm' if self.color_temp < 0.4 else 'cool' if self.color_temp > 0.6 else 'neutral'}",
            f"Saturation: {'low' if self.saturation_mean < 0.3 else 'high' if self.saturation_mean > 0.6 else 'medium'}",
        ]
        return "\n".join(lines)

    def diagnose(self) -> list[str]:
        """Return list of potential issues detected."""
        issues = []

        # Exposure
        if self.brightness_mean < 30:
            issues.append("UNDEREXPOSED: Image is very dark (mean < 30)")
        elif self.brightness_mean < 60:
            issues.append("DARK: Image is darker than typical (mean < 60)")
        elif self.brightness_mean > 220:
            issues.append("OVEREXPOSED: Image is very bright (mean > 220)")
        elif self.brightness_mean > 180:
            issues.append("BRIGHT: Image is brighter than typical (mean > 180)")

        # Contrast
        if self.brightness_std < 20:
            issues.append("LOW_CONTRAST: Low dynamic range (std < 20)")
        if self.contrast_ratio > 100:
            issues.append("HIGH_CONTRAST: Extreme contrast ratio (> 100x)")

        # Saturation
        if self.saturation_mean < 0.1:
            issues.append("DESATURATED: Very low color saturation")

        # Color cast
        if self.color_temp < 0.3:
            issues.append("VERY_WARM: Strong warm/red color cast")
        elif self.color_temp > 0.7:
            issues.append("VERY_COOL: Strong cool/blue color cast")

        # Clipping
        total = sum(self.histogram)
        if total > 0:
            shadow_pct = self.histogram[0] / total
            highlight_pct = self.histogram[-1] / total

            if shadow_pct > 0.1:
                issues.append(f"SHADOW_CLIPPING: {shadow_pct*100:.1f}% pixels crushed")
            if highlight_pct > 0.1:
                issues.append(f"HIGHLIGHT_CLIPPING: {highlight_pct*100:.1f}% pixels blown")

        return issues


def read_png_pixels(path: str | Path) -> tuple[list[tuple[int, int, int]], int, int]:
    """
    Read RGB pixel data from a PNG file.
    Returns (pixels, width, height) where pixels is list of (r, g, b) tuples.
    """
    path = Path(path)

    with open(path, "rb") as f:
        # Verify signature
        sig = f.read(8)
        if sig != b"\x89PNG\r\n\x1a\n":
            raise ValueError("Not a valid PNG file")

        width = height = 0
        bit_depth = color_type = 0
        idat_data = b""

        while True:
            header = f.read(8)
            if len(header) < 8:
                break

            length = struct.unpack(">I", header[:4])[0]
            chunk_type = header[4:8]
            chunk_data = f.read(length)
            f.read(4)  # CRC

            if chunk_type == b"IHDR":
                width = struct.unpack(">I", chunk_data[0:4])[0]
                height = struct.unpack(">I", chunk_data[4:8])[0]
                bit_depth = chunk_data[8]
                color_type = chunk_data[9]
            elif chunk_type == b"IDAT":
                idat_data += chunk_data
            elif chunk_type == b"IEND":
                break

        if width == 0 or height == 0:
            raise ValueError("Could not read PNG dimensions")

        # Decompress
        raw = zlib.decompress(idat_data)

        # Parse scanlines
        pixels = []
        bpp = 3 if color_type == 2 else 4 if color_type == 6 else 1
        row_bytes = width * bpp

        pos = 0
        prev_row = bytes(row_bytes)

        for _ in range(height):
            filter_type = raw[pos]
            pos += 1
            row = bytearray(raw[pos : pos + row_bytes])
            pos += row_bytes

            # Apply filter
            if filter_type == 1:  # Sub
                for i in range(bpp, row_bytes):
                    row[i] = (row[i] + row[i - bpp]) & 0xFF
            elif filter_type == 2:  # Up
                for i in range(row_bytes):
                    row[i] = (row[i] + prev_row[i]) & 0xFF
            elif filter_type == 3:  # Average
                for i in range(row_bytes):
                    left = row[i - bpp] if i >= bpp else 0
                    row[i] = (row[i] + (left + prev_row[i]) // 2) & 0xFF
            elif filter_type == 4:  # Paeth
                for i in range(row_bytes):
                    left = row[i - bpp] if i >= bpp else 0
                    up = prev_row[i]
                    up_left = prev_row[i - bpp] if i >= bpp else 0
                    p = left + up - up_left
                    pa, pb, pc = abs(p - left), abs(p - up), abs(p - up_left)
                    pr = left if pa <= pb and pa <= pc else up if pb <= pc else up_left
                    row[i] = (row[i] + pr) & 0xFF

            prev_row = bytes(row)

            for x in range(width):
                offset = x * bpp
                pixels.append((row[offset], row[offset + 1], row[offset + 2]))

        return pixels, width, height


def analyze(path: str | Path) -> ImageMetrics:
    """Analyze a screenshot and return metrics."""
    path = Path(path)
    pixels, width, height = read_png_pixels(path)

    brightnesses = []
    saturations = []
    red_sum = green_sum = blue_sum = 0

    for r, g, b in pixels:
        # Luminance
        brightness = 0.299 * r + 0.587 * g + 0.114 * b
        brightnesses.append(brightness)

        # Saturation
        max_c = max(r, g, b)
        min_c = min(r, g, b)
        saturations.append((max_c - min_c) / max_c if max_c > 0 else 0)

        red_sum += r
        green_sum += g
        blue_sum += b

    # Stats
    n = len(brightnesses)
    brightness_mean = sum(brightnesses) / n
    brightness_std = (sum((b - brightness_mean) ** 2 for b in brightnesses) / n) ** 0.5
    brightness_min = int(min(brightnesses))
    brightness_max = int(max(brightnesses))

    contrast_ratio = brightness_max / max(brightness_min, 1)
    total_rb = red_sum + blue_sum
    color_temp = blue_sum / total_rb if total_rb > 0 else 0.5
    saturation_mean = sum(saturations) / n

    # Histogram (16 bins)
    histogram = [0] * 16
    for b in brightnesses:
        histogram[min(int(b / 16), 15)] += 1

    return ImageMetrics(
        brightness_mean=brightness_mean,
        brightness_std=brightness_std,
        brightness_min=brightness_min,
        brightness_max=brightness_max,
        contrast_ratio=contrast_ratio,
        color_temp=color_temp,
        saturation_mean=saturation_mean,
        histogram=histogram,
        width=width,
        height=height,
        path=str(path),
    )


@dataclass
class ComparisonResult:
    """Result of comparing two images."""
    path_a: str
    path_b: str
    brightness_diff: float
    contrast_diff: float
    color_temp_diff: float
    saturation_diff: float
    histogram_correlation: float

    def to_dict(self) -> dict:
        return {
            "path_a": self.path_a,
            "path_b": self.path_b,
            "differences": {
                "brightness": round(self.brightness_diff, 2),
                "contrast": round(self.contrast_diff, 2),
                "color_temp": round(self.color_temp_diff, 3),
                "saturation": round(self.saturation_diff, 3),
            },
            "histogram_correlation": round(self.histogram_correlation, 3),
        }


def compare(path_a: str | Path, path_b: str | Path) -> ComparisonResult:
    """Compare two images and return difference metrics."""
    a = analyze(path_a)
    b = analyze(path_b)

    # Differences
    brightness_diff = b.brightness_mean - a.brightness_mean
    contrast_diff = b.contrast_ratio - a.contrast_ratio
    color_temp_diff = b.color_temp - a.color_temp
    saturation_diff = b.saturation_mean - a.saturation_mean

    # Histogram correlation (Pearson)
    ha, hb = a.histogram, b.histogram
    n = len(ha)
    mean_a = sum(ha) / n
    mean_b = sum(hb) / n
    cov = sum((ha[i] - mean_a) * (hb[i] - mean_b) for i in range(n))
    std_a = (sum((ha[i] - mean_a) ** 2 for i in range(n))) ** 0.5
    std_b = (sum((hb[i] - mean_b) ** 2 for i in range(n))) ** 0.5
    correlation = cov / (std_a * std_b) if std_a > 0 and std_b > 0 else 0

    return ComparisonResult(
        path_a=str(path_a),
        path_b=str(path_b),
        brightness_diff=brightness_diff,
        contrast_diff=contrast_diff,
        color_temp_diff=color_temp_diff,
        saturation_diff=saturation_diff,
        histogram_correlation=correlation,
    )


def analyze_directory(directory: str | Path) -> list[ImageMetrics]:
    """Analyze all PNG files in a directory."""
    directory = Path(directory)
    results = []

    for path in sorted(directory.glob("*.png")):
        try:
            results.append(analyze(path))
        except Exception as e:
            print(f"Warning: Failed to analyze {path}: {e}")

    return results
