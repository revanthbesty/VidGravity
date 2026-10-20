attribute vec4 aFramePosition;
varying vec2 vTex;
void main() {
  gl_Position = aFramePosition;
  vTex = (aFramePosition.xy + 1.0) * 0.5;
}
