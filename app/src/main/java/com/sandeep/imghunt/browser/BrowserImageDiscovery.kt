package com.sandeep.imghunt.browser

/** Runs only after Find Images. Reads the loaded document, not private APIs, hidden frames or cookies. */
object BrowserImageDiscovery {
    val script = """(function(){
      var out=[], seen={}, budget=0;
      function absolute(v){try{return new URL((v||'').trim(),document.baseURI).href;}catch(e){return '';}}
      function allowed(v){return v.indexOf('https://')===0 && v.length<=8192;}
      function option(arr,v,w,h,kind){v=absolute(v);if(!allowed(v))return;
        for(var j=0;j<arr.length;j++)if(arr[j].url===v){if(w>arr[j].width)arr[j].width=w;return;}
        if(arr.length<16)arr.push({url:v,width:Math.round(w||0),height:Math.round(h||0),kind:kind});}
      function srcset(arr,text,natural){
        if(!text||text.indexOf('data:')>=0)return;
        var p=0;while(p<text.length){
          while(p<text.length && /[\s,]/.test(text[p]))p++;
          var s=p;while(p<text.length && !/\s/.test(text[p]))p++;
          var url=text.slice(s,p), ended=/,$/.test(url);url=url.replace(/,+$/,'');
          if(ended){option(arr,url,0,0,'Responsive image');continue;}
          s=p;while(p<text.length && text[p]!==',')p++;
          var d=text.slice(s,p).trim(),m=/^(\d+(?:\.\d+)?)(w|x)$/.exec(d);p++;
          option(arr,url,m?(m[2]==='w'?Number(m[1]):Number(m[1])*(natural||0)):0,0,'Responsive image');
        }
      }
      function hidden(el){var st=getComputedStyle(el);return st.display==='none'||st.visibility==='hidden'||!!el.closest('[aria-hidden="true"],[data-ad],[id^="google_ads"],iframe');}
      function push(el,src,options,w,h,title,anchor){
        if(out.length>=80||hidden(el))return;
        var rect=el.getBoundingClientRect();
        if(Math.max(w||0,rect.width)<80||Math.max(h||0,rect.height)<80){
          if(!options.some(function(o){return o.width>=512||o.kind==='Original link'||o.kind==='Original hint';}))return;
        }
        src=absolute(src);
        if(!allowed(src) && !(src.indexOf('data:image/')===0&&src.length<=262144))src=options.length?options[0].url:'';
        if(!src||/doubleclick|googlesyndication|tracking[_-]?pixel/i.test(src))return;
        var key=options.length?options[0].url:src;if(seen[key])return;
        var row={src:src,anchor:anchor||'',title:(title||document.title||'Website image').slice(0,160),width:w||0,height:h||0,options:options};
        var cost=JSON.stringify(row).length;if(budget+cost>500000)return;
        budget+=cost;seen[key]=true;out.push(row);
      }
      var images=document.images;
      for(var i=0;i<images.length&&out.length<80;i++){
        var im=images[i],a=im.closest('a'),anchor=a?a.href:'',opts=[],w=im.naturalWidth||0,h=im.naturalHeight||0;
        option(opts,im.currentSrc||im.src,w,h,'Displayed image');
        srcset(opts,im.getAttribute('srcset'),w);srcset(opts,im.getAttribute('data-srcset'),w);
        var picture=im.closest('picture');if(picture){var sources=picture.querySelectorAll('source[srcset]');for(var k=0;k<sources.length;k++)srcset(opts,sources[k].getAttribute('srcset'),w);}
        ['data-src','data-lazy-src','data-original','data-full','data-zoom-image','data-large-file','data-hires'].forEach(function(attr){
          option(opts,im.getAttribute(attr),0,0,/original|full|zoom|large|hires/.test(attr)?'Original hint':'Lazy image');});
        if(anchor){try{var u=new URL(anchor);var original=u.searchParams.get('imgurl');
          if((u.hostname==='google.com'||/\.google\.com$/.test(u.hostname))&&original)option(opts,original,0,0,'Original link');
          if(/\.(jpe?g|png|webp|gif|avif)(?:[?#]|$)/i.test(u.href))option(opts,u.href,0,0,'Original link');
        }catch(e){}}
        opts.sort(function(x,y){function rank(o){return o.kind==='Original link'?10000000:o.kind==='Original hint'?9000000:o.width;}return rank(y)-rank(x);});
        push(im,im.currentSrc||im.src,opts,w,h,im.alt,anchor);
      }
      var nodes=document.querySelectorAll('body *');
      for(var n=0;n<Math.min(nodes.length,1500)&&out.length<80;n++){
        var el=nodes[n];if(/^(IMG|SCRIPT|STYLE|IFRAME|SOURCE)$/.test(el.tagName)||hidden(el))continue;
        var r=el.getBoundingClientRect();if(r.width<80||r.height<80)continue;
        var bg=getComputedStyle(el).backgroundImage,reg=/url\((?:"([^"]*)"|'([^']*)'|([^)]*))\)/g,m;
        while((m=reg.exec(bg))!==null){var options=[];option(options,m[1]||m[2]||m[3],0,0,'CSS background');
          if(options.length)push(el,options[0].url,options,0,0,el.getAttribute('aria-label')||'Page background image','');}
      }
      return JSON.stringify(out);
    })()""".trimIndent()
}

data class BrowserImageOption(val url: String, val width: Int = 0, val height: Int = 0, val kind: String = "Page image")
data class BrowserDetectedImage(val photo: com.sandeep.imghunt.engine.PhotoItem,
    val options: List<BrowserImageOption>, val selectedOption: Int = 0) {
    val quality: String get() {
        val url = photo.highResUrl
        return when {
            url.startsWith("file:") || url.contains("encrypted-tbn") -> "Preview only"
            options.getOrNull(selectedOption)?.kind?.startsWith("Original") == true -> "Original candidate — unverified"
            else -> "Page image — size unverified"
        }
    }
}
