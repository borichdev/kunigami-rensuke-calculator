#!/usr/bin/env python3
import os
import math
import subprocess
import tempfile
from PIL import Image, ImageFilter, ImageEnhance

PROJ_DIR = "/home/borichdev/projects/kunigamicalculator"
RAW_DIR = os.path.join(PROJ_DIR, "assets_raw")
OUT_DIR = os.path.join(PROJ_DIR, "prod", "assets", "textures")
os.makedirs(OUT_DIR, exist_ok=True)

TEXTURES = [
    ("T_common_num888_col.png", 4),
    ("T_common_num888_sdw.png", 4),
    ("T_CH001002_shoe_col.png", 4),
    ("T_CH001002_shoe_sdw.png", 4),
    ("T_common_innerA_col.png", 2),
    ("T_common_innerA_sdw.png", 2),
    ("T_CH003002_innerOth_col.png", 4),
    ("T_CH003002_innerOth_sdw.png", 4),
    ("T_common_uniformF_col.png", 2),
    ("T_common_uniformF_sdw.png", 2),
    ("T_CH003004_uniformNumF_col.png", 4),
    ("T_CH003004_uniformNumF_sdw.png", 4),
    ("T_CH003002_hair_col.png", 2),
    ("T_CH003002_hair_sdw.png", 2),
    ("T_CH003002_hair_sh.png", 4),
    ("T_CH003002_colPalette_col.png", 4),
    ("T_CH003002_colPalette_sdw.png", 4),
    ("T_CH003002_otherFace_col.png", 4),
    ("T_CH003002_otherFace_sdw.png", 4),
    ("T_CH003002_eye_col.png", 4),
    ("T_CH003002_eyeBrow_col.png", 4),
    ("T_CH003002_eyeBrow_sdw.png", 4),
    ("T_common_eyeEgo_col.png", 4),
]

def run_waifu2x(src_path, dst_path, scale):
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
        tmp_path = tmp.name
    try:
        if scale in (1, 2):
            cmd = ["/usr/bin/waifu2x-ncnn-vulkan", "-i", src_path, "-o", dst_path, "-n", "2", "-s", str(scale)]
            subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        elif scale == 4:
            cmd1 = ["/usr/bin/waifu2x-ncnn-vulkan", "-i", src_path, "-o", tmp_path, "-n", "2", "-s", "2"]
            subprocess.run(cmd1, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            cmd2 = ["/usr/bin/waifu2x-ncnn-vulkan", "-i", tmp_path, "-o", dst_path, "-n", "1", "-s", "2"]
            subprocess.run(cmd2, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)

def enhance_num50(img_path, is_sdw=False):
    img = Image.open(img_path).convert("RGBA")
    w, h = img.size
    px = img.load()
    bg = px[2, 2][:3]
    fg = px[int(w * 0.16), int(h * 0.16)][:3]
    span = max(1.0, float(bg[0] - fg[0]))
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            t = max(0.0, min(1.0, (r - fg[0]) / span))
            t = t * t * (3.0 - 2.0 * t)
            nr = int(fg[0] * (1.0 - t) + bg[0] * t)
            ng = int(fg[1] * (1.0 - t) + bg[1] * t)
            nb = int(fg[2] * (1.0 - t) + bg[2] * t)
            px[x, y] = (nr, ng, nb, a)
    img.save(img_path)

def enhance_eye(img_path):
    img = Image.open(img_path).convert("RGBA")
    w, h = img.size
    px = img.load()
    cx, cy = w * 0.5, h * 0.5
    for y in range(h):
        dy = (y - cy) / (h * 0.5)
        for x in range(w):
            r, g, b, a = px[x, y]
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            dx = (x - cx) / (w * 0.5)
            rad = math.sqrt(dx * dx + dy * dy)
            if r > b * 1.25 and 35.0 < lum < 225.0 and rad < 0.72:
                theta = math.atan2(dy, dx)
                striations = 0.5 + 0.5 * math.sin(theta * 36.0 + math.sin(rad * 18.0) * 1.5)
                radial_ring = math.exp(-((rad - 0.36) / 0.14) ** 2)
                lower_glow = max(0.0, min(1.0, (dy + 0.15) / 0.55)) * math.exp(-((rad - 0.32) / 0.22) ** 2)
                nr = min(255, int(r + lower_glow * 34.0 + striations * radial_ring * 16.0))
                ng = min(255, int(g + lower_glow * 22.0 + striations * radial_ring * 9.0))
                nb = min(255, int(b + lower_glow * 4.0))
                px[x, y] = (nr, ng, nb, a)
    img = img.filter(ImageFilter.UnsharpMask(radius=1.4, percent=135, threshold=2))
    img.save(img_path)

def enhance_sharp_details(img_path, contrast=1.06, sharpness=130):
    img = Image.open(img_path).convert("RGBA")
    rgb = img.convert("RGB")
    if contrast != 1.0:
        rgb = ImageEnhance.Contrast(rgb).enhance(contrast)
    rgb = rgb.filter(ImageFilter.UnsharpMask(radius=1.2, percent=sharpness, threshold=2))
    r, g, b = rgb.split()
    a = img.split()[3]
    Image.merge("RGBA", (r, g, b, a)).save(img_path)

def bake_hair_highlights(hair_col_path, hair_sh_path):
    col_img = Image.open(hair_col_path).convert("RGBA")
    w, h = col_img.size
    sh_img = Image.open(hair_sh_path).convert("RGBA").resize((w, h), Image.Resampling.BILINEAR)
    c_px = col_img.load()
    s_px = sh_img.load()
    for y in range(h):
        for x in range(w):
            sr, sg, sb, _ = s_px[x, y]
            diff = sg - sr
            if diff > 10:
                streak = min(1.0, (diff - 10.0) / 85.0)
                cr, cg, cb, ca = c_px[x, y]
                nr = min(255, int(cr + 78.0 * streak))
                ng = min(255, int(cg + 58.0 * streak))
                nb = min(255, int(cb + 24.0 * streak))
                c_px[x, y] = (nr, ng, nb, ca)
    col_img.save(hair_col_path)

def main():
    for fname, scale in TEXTURES:
        src = os.path.join(RAW_DIR, fname)
        dst = os.path.join(OUT_DIR, fname)
        run_waifu2x(src, dst, scale)
        if "uniformNumF" in fname:
            enhance_num50(dst, is_sdw=("_sdw" in fname))
        elif fname == "T_CH003002_eye_col.png":
            enhance_eye(dst)
        elif "num888" in fname or "uniformF" in fname or "hair_col" in fname:
            enhance_sharp_details(dst, contrast=1.05, sharpness=125)
        with Image.open(dst) as im:
            print(f"Upgraded {fname:32s} -> {im.size[0]}x{im.size[1]}")
    bake_hair_highlights(
        os.path.join(OUT_DIR, "T_CH003002_hair_col.png"),
        os.path.join(OUT_DIR, "T_CH003002_hair_sh.png")
    )
    print("Baked anime hair highlights from T_CH003002_hair_sh.png into T_CH003002_hair_col.png")

if __name__ == "__main__":
    main()
