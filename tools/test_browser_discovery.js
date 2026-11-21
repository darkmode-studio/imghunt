'use strict';
const fs=require('fs'), vm=require('vm'), assert=require('node:assert/strict');
const source=fs.readFileSync('app/src/main/java/com/sandeep/imghunt/browser/BrowserImageDiscovery.kt','utf8');
const script=source.match(/val script = """([\s\S]*?)"""\.trimIndent\(\)/)[1];
function element(attrs={},extra={}) {
  return Object.assign({tagName:'IMG',currentSrc:attrs.src||'',src:attrs.src||'',naturalWidth:320,naturalHeight:240,alt:'Fixture',
    getAttribute:name=>attrs[name]||null,closest:()=>null,getBoundingClientRect:()=>({width:320,height:240}),
    computed:{display:'block',visibility:'visible',backgroundImage:'none'}},extra);
}
function scan(images,nodes=[]) {
  const document={baseURI:'https://fixture.example/page',title:'Fixture',images,querySelectorAll:()=>nodes};
  return JSON.parse(vm.runInNewContext(script,{document,URL,getComputedStyle:e=>e.computed},{timeout:1000}));
}
let rows=scan([element({src:'/thumb.jpg',srcset:'/medium.jpg 640w, /full.jpg 2048w'}),element({'data-src':'/lazy.jpg'},{src:'',currentSrc:'',naturalWidth:0,naturalHeight:0})]);
assert.equal(rows.length,2);
assert.equal(rows[0].options[0].url,'https://fixture.example/full.jpg');
assert.equal(rows[0].options[0].width,2048);
assert.equal(rows[1].src,'https://fixture.example/lazy.jpg');
assert.ok(rows.every(r=>r.options.every(o=>o.url!=='https://fixture.example/page')));
rows=scan([], [element({}, {tagName:'DIV',computed:{display:'block',visibility:'visible',backgroundImage:'url("https://fixture.example/background.jpg")'}})]);
assert.equal(rows[0].options[0].kind,'CSS background');
assert.equal(scan([element({src:'/pixel.png'},{naturalWidth:1,naturalHeight:1,getBoundingClientRect:()=>({width:1,height:1})})]).length,0);
assert.equal(scan([element({src:'/hidden.jpg'},{computed:{display:'none',visibility:'visible',backgroundImage:'none'}})]).length,0);
rows=scan([element({src:'/thumb.jpg'},{closest:s=>s==='a'?{href:'https://www.google.com/imgres?imgurl='+encodeURIComponent('https://cdn.example/full.jpg')}:null})]);
assert.equal(rows[0].options[0].kind,'Original link');
assert.equal(rows[0].options[0].url,'https://cdn.example/full.jpg');
console.log('Browser discovery: responsive, lazy, CSS, empty-attribute, tiny/hidden image and original-link checks passed');
