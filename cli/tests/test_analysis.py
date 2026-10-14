import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mccli.analysis import ImageMetrics


def metrics_with_missing_texture_percent(percent: float) -> ImageMetrics:
    return ImageMetrics(
        brightness_mean=120.0,
        brightness_std=40.0,
        brightness_min=10,
        brightness_max=230,
        contrast_ratio=23.0,
        color_temp=0.5,
        saturation_mean=0.4,
        histogram=[0] * 16,
        missing_texture_pixels=10,
        missing_texture_percent=percent,
        width=100,
        height=100,
        path="screenshot.png",
    )


class ImageMetricsMissingTextureTest(unittest.TestCase):
    def test_possible_missing_texture_is_not_likely(self):
        metrics = metrics_with_missing_texture_percent(0.5)

        self.assertIn("MISSING_TEXTURE_POSSIBLE", metrics.diagnose()[0])
        self.assertTrue(metrics.to_dict()["missing_texture"]["possible"])
        self.assertFalse(metrics.to_dict()["missing_texture"]["likely"])

    def test_likely_missing_texture_matches_diagnosis(self):
        metrics = metrics_with_missing_texture_percent(2.0)

        self.assertIn("MISSING_TEXTURE_LIKELY", metrics.diagnose()[0])
        self.assertTrue(metrics.to_dict()["missing_texture"]["possible"])
        self.assertTrue(metrics.to_dict()["missing_texture"]["likely"])


if __name__ == "__main__":
    unittest.main()
