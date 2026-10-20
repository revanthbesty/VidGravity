#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform vec4 uGradeA;
uniform vec4 uGradeB;
uniform vec4 uGradeC;
uniform int uLook;
uniform float uTime;
uniform vec2 uTransition;
varying vec2 vTex;

// Media3 1.5.1 DefaultVideoFrameProcessor uses WORKING_COLOR_SPACE_DEFAULT:
// SDR textures already carry the SMPTE 170m/BT.709 transfer. Only exposure needs
// a conversion to linear light; keep input/output in the existing working space.
vec3 to709(vec3 c) {
  c = max(c, vec3(0.0));
  return mix(4.5*c, 1.099*pow(c,vec3(0.45))-0.099, step(vec3(0.018),c));
}
vec3 from709(vec3 c) {
  c=clamp(c,0.0,1.0);
  return mix(c/4.5, pow((c+0.099)/1.099,vec3(1.0/0.45)),step(vec3(0.081),c));
}
vec3 hueRotate(vec3 c,float angle) {
  float y=dot(c,vec3(0.299,0.587,0.114));
  float i=dot(c,vec3(0.596,-0.274,-0.322));
  float q=dot(c,vec3(0.211,-0.523,0.312));
  float a=i*cos(angle)-q*sin(angle);
  float b=i*sin(angle)+q*cos(angle);
  return vec3(y+0.956*a+0.621*b,y-0.272*a-0.647*b,y-1.106*a+1.703*b);
}
vec3 sampleAt(vec2 delta) {return texture2D(uTexSampler,clamp(vTex+delta,vec2(0.0),vec2(1.0))).rgb;}
vec3 grade(vec3 raw) {
  vec3 c=raw;
  if(abs(uGradeA.w)>0.00001) c=to709(from709(raw)*exp2(uGradeA.w));
  c+=uGradeA.x;
  c=(c-0.5)*exp2(uGradeA.y*2.0)+0.5;
  float l=dot(c,vec3(0.2126,0.7152,0.0722));
  c=mix(vec3(l),c,1.0+uGradeA.z);
  c*=vec3(1.0+0.2*uGradeB.x,1.0,1.0-0.2*uGradeB.x);
  c+=uGradeB.y*vec3(0.08,-0.14,0.08);
  c+=vec3(uGradeB.z*0.35*smoothstep(0.4,1.0,l)+uGradeB.w*0.35*(1.0-smoothstep(0.0,0.6,l)));
  c=pow(max(c,vec3(0.0)),vec3(1.0/uGradeC.x));
  if(abs(uGradeC.y)>0.00001) c=hueRotate(c,uGradeC.y);
  return clamp(c,0.0,1.0);
}
void main() {
  vec3 raw=sampleAt(vec2(0.0));
  vec3 sharp=raw;
  if(uGradeC.z>0.0) {
    vec3 near=(sampleAt(vec2(uTexel.x,0.0))+sampleAt(vec2(-uTexel.x,0.0))+sampleAt(vec2(0.0,uTexel.y))+sampleAt(vec2(0.0,-uTexel.y)))*0.25;
    sharp+=(raw-near)*uGradeC.z;
  }
  vec3 c=grade(sharp);
  vec3 looked=c;
  vec2 d=uTexel*6.0;
  vec3 blur=c;
  if(uLook==8 || uLook==10 || uLook==13) blur=grade((raw+sampleAt(d)+sampleAt(-d)+sampleAt(vec2(d.x,-d.y))+sampleAt(vec2(-d.x,d.y))+sampleAt(vec2(d.x,0.0))+sampleAt(vec2(-d.x,0.0))+sampleAt(vec2(0.0,d.y))+sampleAt(vec2(0.0,-d.y)))/9.0);
  if(uLook==1) looked=c*vec3(1.12,1.03,0.87)+vec3(0.015,0.005,0.0);
  else if(uLook==2) looked=c*vec3(0.88,1.02,1.14);
  else if(uLook==3) looked=vec3(dot(c,vec3(0.2126,0.7152,0.0722)));
  else if(uLook==4) looked=mix(vec3(dot(c,vec3(0.2126,0.7152,0.0722))),c,0.62)*vec3(0.98,0.9,0.75)+vec3(0.06,0.04,0.02);
  else if(uLook==5) looked=vec3(dot(c,vec3(0.393,0.769,0.189)),dot(c,vec3(0.349,0.686,0.168)),dot(c,vec3(0.272,0.534,0.131)));
  else if(uLook==6) {
    float noise=fract(sin(dot(floor(vTex/uTexel),vec2(12.9898,78.233))+uTime*7.1)*43758.5453)-0.5;
    looked=c+vec3(noise*0.13);
  }
  else if(uLook==7) looked=c*(1.0-0.75*smoothstep(0.18,0.7,length(vTex-0.5)));
  else if(uLook==8) looked=c+blur*0.3;
  else if(uLook==9) looked=(c-0.5)*1.6+0.5;
  else if(uLook==10) looked=mix(c,blur,0.45)*vec3(0.95,1.02,1.08)+0.06;
  else if(uLook==11) looked=pow(c,vec3(1.3))*1.05;
  else if(uLook==12) looked=c*0.78+0.12;
  else if(uLook==13) looked=blur;
  vec3 result=mix(c,clamp(looked,0.0,1.0),uGradeC.w);
  result=mix(result,vec3(uTransition.y),uTransition.x);
  gl_FragColor=vec4(clamp(result,0.0,1.0),1.0);
}
