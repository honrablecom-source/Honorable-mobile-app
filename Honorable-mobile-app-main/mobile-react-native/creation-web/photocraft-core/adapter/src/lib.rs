//! Narrow mobile adapter over the real, pinned PhotoCraft processing libraries.
//! No file parser, networking, arbitrary command dispatch or desktop UI is exposed.
#![forbid(unsafe_code)]

use photocraft_algo::{Ctx, FilterParams, Halo, Image, kernel};
use photocraft_color::{BlendMode, ColorMode, blend::composite};
use photocraft_geom::Rect;
use wasm_bindgen::prelude::*;

const MAX_SIDE: u32 = 512;

#[wasm_bindgen]
pub fn core_revision() -> String {
    "PhotoCraft f143ce58498f201a8dfc9c07f5e24147c6a22bee; Noble mobile adapter1".to_owned()
}

fn frame(width: u32, height: u32, rgba: &[u8]) -> Result<Rect, String> {
    if width == 0 || height == 0 || width > MAX_SIDE || height > MAX_SIDE {
        return Err("Use a local image from1 to512 pixels per side".into());
    }
    let bytes = (width as usize).checked_mul(height as usize).and_then(|n| n.checked_mul(4));
    if bytes != Some(rgba.len()) {
        return Err("The local RGBA image is incomplete".into());
    }
    Ok(Rect::new(0, 0, width as i32, height as i32))
}

fn parameters(effect: &str, value: f32) -> Result<FilterParams, String> {
    if !value.is_finite() {
        return Err("Use finite effect controls".into());
    }
    let valid = |low, high| value >= low && value <= high;
    match effect {
        "emboss" if valid(0.0, 300.0) => Ok(FilterParams::Emboss {angle: 45.0, height: 2.0, amount: value}),
        "findEdges" if value == 0.0 => Ok(FilterParams::FindEdges),
        "highPass" if valid(0.5, 12.0) => Ok(FilterParams::HighPass {radius: value}),
        "gaussianBlur" if valid(0.5, 12.0) => Ok(FilterParams::GaussianBlur {radius: value}),
        "surfaceBlur" if valid(1.0, 6.0) => Ok(FilterParams::SurfaceBlur {radius: value, threshold: 16.0}),
        "smartSharpen" if valid(0.0, 150.0) => Ok(FilterParams::SmartSharpen {amount: value, radius: 1.5, reduce_noise: 10.0}),
        "unsharpMask" if valid(0.0, 150.0) => Ok(FilterParams::UnsharpMask {amount: value, radius: 1.5, threshold: 3.0}),
        "mosaic" if valid(2.0, 32.0) => Ok(FilterParams::Mosaic {cell_size: value}),
        "twirl" if valid(-180.0, 180.0) => Ok(FilterParams::Twirl {angle: value}),
        "solarize" if value == 0.0 => Ok(FilterParams::Solarize),
        "invert" if value == 0.0 => Ok(FilterParams::Invert),
        "desaturate" if value == 0.0 => Ok(FilterParams::Desaturate),
        _ => Err("Choose a supported bounded PhotoCraft effect".into()),
    }
}

#[wasm_bindgen]
pub fn apply_filter(width: u32, height: u32, rgba: &[u8], effect: &str, value: f32) -> Result<Vec<u8>, String> {
    let bounds = frame(width, height, rgba)?;
    let params = parameters(effect, value)?;
    let halo = match params.halo_for(bounds) {Halo::Radius(n) if (0..=64).contains(&n) => n, Halo::Bounds => 0,
        _ => return Err("The effect needs too much temporary memory".into())};
    let extent = Rect::new(-halo, -halo, width as i32 + halo, height as i32 + halo);
    let samples = (extent.width() as usize).checked_mul(extent.height() as usize).and_then(|n| n.checked_mul(4))
        .ok_or_else(|| "Effect dimensions overflowed".to_owned())?;
    let mut data = Vec::new();
    data.try_reserve_exact(samples).map_err(|_| "Not enough memory for the local effect".to_owned())?;
    // Repeat canvas edges for the declared halo, matching the core's apply_in extent convention.
    for y in extent.y0..extent.y1 {
        let sy = y.clamp(0, height as i32 - 1) as usize;
        for x in extent.x0..extent.x1 {
            let sx = x.clamp(0, width as i32 - 1) as usize;
            let index = (sy * width as usize + sx) * 4;
            let pixel = rgba.get(index..index + 4).ok_or_else(|| "Source pixel is unavailable".to_owned())?;
            data.extend(pixel.iter().map(|v| *v as f32 / 255.0));
        }
    }
    let source = Image {rect: extent, ch: 4, data};
    let result = kernel(&params, &source, bounds, &Ctx {bounds, mode: ColorMode::Rgb, alpha: true});
    if result.len() != rgba.len() || result.iter().any(|v| !v.is_finite()) {
        return Err("PhotoCraft returned an invalid image".into());
    }
    Ok(result.iter().map(|v| (v.clamp(0.0, 1.0) * 255.0).round() as u8).collect())
}

#[wasm_bindgen]
pub fn blend_layers(width: u32, height: u32, backdrop: &[u8], source: &[u8], mode: &str, opacity: f32) -> Result<Vec<u8>, String> {
    frame(width, height, backdrop)?;
    frame(width, height, source)?;
    if !opacity.is_finite() || !(0.0..=1.0).contains(&opacity) {return Err("Use layer opacity from0 to1".into());}
    let blend = match mode {"normal" => BlendMode::Normal, "multiply" => BlendMode::Multiply,
        "screen" => BlendMode::Screen, "overlay" => BlendMode::Overlay, "softLight" => BlendMode::SoftLight,
        _ => return Err("Choose a supported layer blend".into())};
    let mut result = Vec::new();
    result.try_reserve_exact(backdrop.len()).map_err(|_| "Not enough memory for layer compositing".to_owned())?;
    for (base, top) in backdrop.chunks_exact(4).zip(source.chunks_exact(4)) {
        let b = [base[0] as f32 / 255.0, base[1] as f32 / 255.0, base[2] as f32 / 255.0, base[3] as f32 / 255.0];
        let s = [top[0] as f32 / 255.0, top[1] as f32 / 255.0, top[2] as f32 / 255.0, top[3] as f32 / 255.0];
        let rgba = composite(blend, b, s, opacity);
        if rgba.iter().any(|v| !v.is_finite()) {return Err("Layer compositing returned invalid pixels".into());}
        result.extend(rgba.iter().map(|v| (v.clamp(0.0, 1.0) * 255.0).round() as u8));
    }
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn invalid_controls_and_frame_lengths_return_errors() {
        let pixel = [30, 60, 90, 255];
        for (width, height) in [(0, 1), (1, 0), (513, 1), (u32::MAX, u32::MAX)] {
            assert!(apply_filter(width, height, &pixel, "invert", 0.0).is_err());
        }
        assert!(apply_filter(1, 1, &[], "invert", 0.0).is_err());
        for (effect, value) in [("unknown", 1.0), ("gaussianBlur", f32::NAN), ("twirl", 10000.0), ("highPass", -1.0)] {
            assert!(apply_filter(1, 1, &pixel, effect, value).is_err());
        }
        assert!(blend_layers(1, 1, &pixel, &pixel, "normal", f32::INFINITY).is_err());
        assert!(blend_layers(1, 1, &pixel, &[], "normal", 1.0).is_err());
        assert!(blend_layers(1, 1, &pixel, &pixel, "unknown", 1.0).is_err());
    }
    #[test]
    fn real_core_invert_retains_alpha_and_original_then_round_trips() {
        let input = [30, 60, 90, 127, 200, 100, 50, 255];
        let result = apply_filter(2, 1, &input, "invert", 0.0).expect("valid fixture");
        assert_eq!(result, [225, 195, 165, 127, 55, 155, 205, 255]);
        assert_eq!(apply_filter(2, 1, &result, "invert", 0.0).expect("valid fixture"), input);
    }
    #[test]
    fn real_core_alpha_composites_instead_of_replacing_the_background() {
        let result = blend_layers(1, 1, &[255, 0, 0, 255], &[0, 0, 255, 128], "normal", 1.0).expect("valid fixture");
        assert_eq!(result, [127, 0, 128, 255]);
        assert_eq!(blend_layers(1, 1, &[255, 0, 0, 255], &[0, 0, 255, 128], "normal", 0.0).expect("valid fixture"), [255, 0, 0, 255]);
    }
    #[test]
    fn repeated_constant_canvas_edges_do_not_create_a_blur_fringe() {
        let input = [90, 120, 150, 255].repeat(64);
        let result = apply_filter(8, 8, &input, "gaussianBlur", 3.0).expect("valid fixture");
        assert_eq!(result, input);
    }
}
