const params=new URLSearchParams(location.search);
const attribution={source:params.get('source')||params.get('utm_source')||'direct',campaign:params.get('campaign')||params.get('utm_campaign')||'organic',content:params.get('content')||params.get('utm_content')||'unknown'};
fetch('/api/events',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({type:'landing_view',...attribution})}).catch(()=>{});
document.querySelector('#waitlist-form').addEventListener('submit',async event=>{
  event.preventDefault();const form=event.currentTarget;const message=form.querySelector('.form-message');const button=form.querySelector('button');button.disabled=true;message.textContent='Joining…';
  const data=new FormData(form);const payload={email:data.get('email'),device:data.get('device'),consent:data.get('consent')==='on',website:data.get('website'),...attribution};
  try{const response=await fetch('/api/waitlist',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(payload)});const result=await response.json();if(!response.ok)throw new Error(result.error||'Unable to join');message.textContent=result.message;form.querySelector('[name=email]').disabled=true;button.textContent='Joined';}
  catch(error){message.textContent=error.message;button.disabled=false;}
});
