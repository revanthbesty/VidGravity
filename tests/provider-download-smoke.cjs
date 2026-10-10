#!/usr/bin/env node
// P136 smoke tests aligned to server.py's actual GET query routes.
const base=(process.env.VIDGRAVITY_BASE_URL||"http://127.0.0.1:5173").replace(/\/+$/,"");
const cases=[];
async function test(name,fn){try{const detail=await fn();cases.push({name,ok:true,detail:detail||"PASS"});}catch(e){cases.push({name,ok:false,detail:String(e.message||e)});}}
async function request(path,options={}){const r=await fetch(base+path,{...options,signal:AbortSignal.timeout(5000)});const text=await r.text();let body;try{body=JSON.parse(text)}catch{body=text}return {status:r.status,body};}
const q=url=>encodeURIComponent(url);
(async()=>{
 await test("health endpoint",async()=>{const r=await request("/api/health");if(r.status!==200||!r.body?.ok)throw Error("Health failed: HTTP "+r.status);return "HTTP 200; ok=true";});
 await test("health capability disclosure",async()=>{const r=await request("/api/health");if(typeof r.body.yt_dlp!=="boolean")throw Error("Missing yt_dlp capability flag");return "yt_dlp="+r.body.yt_dlp;});
 await test("malformed URL rejection",async()=>{const r=await request("/api/inspect?url="+q("not-a-url"));if(r.status!==400)throw Error("Expected 400, got "+r.status);return "HTTP 400";});
 await test("loopback URL rejection",async()=>{const r=await request("/api/inspect?url="+q("http://127.0.0.1/"));if(r.status!==400)throw Error("Expected 400, got "+r.status);return "HTTP 400";});
 await test("localhost URL rejection",async()=>{const r=await request("/api/formats?url="+q("http://localhost/"));if(r.status!==400)throw Error("Expected 400, got "+r.status);return "HTTP 400";});
 await test("link-local URL rejection",async()=>{const r=await request("/api/formats?url="+q("http://169.254.169.254/latest/meta-data/"));if(r.status!==400)throw Error("Expected 400, got "+r.status);return "HTTP 400";});
 await test("formats route contract",async()=>{const r=await request("/api/formats?url="+q("https://example.com/video.mp4"));if(r.status!==200&&r.status!==502)throw Error("Unexpected HTTP "+r.status);if(r.status===200&&(!r.body||!Array.isArray(r.body.formats)))throw Error("Missing formats array");return "HTTP "+r.status+"; provider availability reported honestly";});
 await test("download route rejects bad URLs",async()=>{const r=await request("/api/download",{method:"POST",headers:{"content-type":"application/json"},body:JSON.stringify({url:"not-a-url"})});if(r.status!==400)throw Error("Expected 400, got "+r.status);return "HTTP 400";});
 await test("download job API contract",async()=>{const r=await request("/api/download-jobs",{method:"POST",headers:{"content-type":"application/json"},body:JSON.stringify({url:"not-a-url"})});if(r.status!==400)throw Error("Expected HTTP 400, got "+r.status);return "Malformed job URL rejected";});
 await test("job retry state validation",async()=>{const bad=await request("/api/download-jobs/not-a-real-id/retry",{method:"POST"});if(bad.status!==404)throw Error("Expected unknown job 404, got "+bad.status);return "Unknown job rejected";});
 const report={baseUrl:base,passed:cases.filter(x=>x.ok).length,failed:cases.filter(x=>!x.ok).length,cases};
 console.log(JSON.stringify(report,null,2));if(report.failed)process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1});
