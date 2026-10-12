//! D24/D33 native-side contracts. Wire this module into the existing Tauri crate before runtime use.
//! Process invocation must use a fixed argument builder, never arbitrary shell strings.
use serde::{Deserialize, Serialize};
use std::path::{Component, Path};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum DownloadState { Queued, WaitingForWindow, Analyzing, Ready, Downloading, Paused, Verifying, Merging, Committing, Completed, Failed, Cancelled }
impl DownloadState {
    pub fn can_transition_to(&self, next: &Self) -> bool {
        use DownloadState::*;
        matches!((self, next),
            (Queued, WaitingForWindow | Analyzing | Cancelled | Failed) |
            (WaitingForWindow, Analyzing | Cancelled | Failed) |
            (Analyzing, Ready | Failed | Cancelled) |
            (Ready, Downloading | Cancelled | Failed) |
            (Downloading, Paused | Verifying | Cancelled | Failed) |
            (Paused, Downloading | Cancelled | Failed) |
            (Verifying, Merging | Committing | Failed | Cancelled) |
            (Merging, Committing | Failed | Cancelled) |
            (Committing, Completed | Failed))
    }
    pub fn require_transition(&self, next: Self) -> Result<Self, &'static str> {
        if self.can_transition_to(&next) { Ok(next) } else { Err("invalid download state transition") }
    }
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "op", rename_all = "snake_case", deny_unknown_fields)]
pub enum FfmpegOperation {
    Input { input_id: String },
    Map { input_id: String, stream: String },
    Filter { input_id: String, filters: Vec<MediaFilter> },
    Copy { input_id: String, output_id: String },
    Mux { video_input_id: Option<String>, audio_input_id: Option<String>, output_id: String, container: String },
    Verify { output_id: String, algorithm: String, expected: Option<String> },
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case", deny_unknown_fields)]
pub enum MediaFilter {
    Scale { width: u32, height: u32, algorithm: String },
    Deinterlace { mode: String },
    Loudnorm { integrated_lufs: f32, true_peak_dbtp: f32, loudness_range_lufs: f32 },
    Format { pixel_format: String },
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct OperationGraph { pub version: u8, pub operations: Vec<FfmpegOperation>, pub output_id: String }

/// Reject absolute paths, parent traversal, prefixes, and empty file names.
/// Native implementation must canonicalize the sandbox root and re-check containment before write.
pub fn validate_relative_output(path: &Path) -> Result<(), &'static str> {
    if path.as_os_str().is_empty() { return Err("empty output path"); }
    if path.is_absolute() { return Err("absolute paths are not allowed"); }
    for component in path.components() {
        match component {
            Component::Normal(_) | Component::CurDir => {},
            _ => return Err("path traversal or platform prefix is not allowed"),
        }
    }
    if path.file_name().is_none() { return Err("output file name is required"); }
    Ok(())
}
pub fn validate_graph(graph: &OperationGraph) -> Result<(), &'static str> {
    if graph.version != 1 || graph.operations.is_empty() { return Err("unsupported or empty operation graph"); }
    let mut output_seen = false;
    for op in &graph.operations {
        match op {
            FfmpegOperation::Filter { filters, .. } => for filter in filters {
                match filter {
                    MediaFilter::Scale { width, height, algorithm } if *width == 0 || *height == 0 || !["bilinear","bicubic","lanczos"].contains(&algorithm.as_str()) => return Err("invalid scale filter"),
                    MediaFilter::Deinterlace { mode } if !["yadif","bwdif"].contains(&mode.as_str()) => return Err("invalid deinterlace mode"),
                    MediaFilter::Loudnorm { integrated_lufs, true_peak_dbtp, loudness_range_lufs } if !integrated_lufs.is_finite() || !true_peak_dbtp.is_finite() || !loudness_range_lufs.is_finite() => return Err("non-finite loudness parameters"),
                    MediaFilter::Format { pixel_format } if !["yuv420p","yuv422p","yuv444p"].contains(&pixel_format.as_str()) => return Err("unsupported pixel format"),
                    _ => {}
                }
            },
            FfmpegOperation::Verify { algorithm, .. } if algorithm != "sha256" => return Err("only sha256 verification is allowed"),
            FfmpegOperation::Mux { container, .. } if !["mp4","webm","mkv","mp3","m4a","wav","flac"].contains(&container.as_str()) => return Err("unsupported output container"),
            FfmpegOperation::Copy { output_id, .. } | FfmpegOperation::Mux { output_id, .. } if output_id == &graph.output_id => output_seen = true,
            _ => {}
        }
    }
    if !output_seen { return Err("graph does not produce its declared output"); }
    Ok(())
}
