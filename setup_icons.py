#!/usr/bin/env python3
"""
Drop your logo PNG anywhere and run:
    python3 setup_icons.py path/to/your_logo.png

This replaces ALL mipmap launcher icons + the drawable/ic_logo used in the app
with correctly-sized versions of your real logo.
"""
import sys, os
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("Installing Pillow…")
    os.system("pip install Pillow --break-system-packages -q")
    from PIL import Image

SIZES = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

RES = Path(__file__).parent / "app/src/main/res"

def main():
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if not src or not src.exists():
        print(f"Usage: python3 setup_icons.py <path-to-logo.png>")
        sys.exit(1)

    logo = Image.open(src).convert("RGBA")

    for folder, size in SIZES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        resized = logo.resize((size, size), Image.LANCZOS)
        # Convert to RGB (no alpha needed for launcher PNGs on older APIs)
        rgb = Image.new("RGB", (size, size), (8, 12, 8))
        rgb.paste(resized, mask=resized.split()[3] if resized.mode == "RGBA" else None)
        rgb.save(out_dir / "ic_launcher.png")
        rgb.save(out_dir / "ic_launcher_round.png")
        print(f"  {folder}/{size}×{size}")

    # 512×512 for Play Store + in-app use
    drawable = RES / "drawable"
    drawable.mkdir(exist_ok=True)
    logo512 = logo.resize((512, 512), Image.LANCZOS)
    logo512.save(drawable / "ic_logo.png")
    print("  drawable/ic_logo.png (512×512)")

    print("\nDone — rebuild the project in Android Studio.")

if __name__ == "__main__":
    main()
