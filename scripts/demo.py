import argparse,json,time,urllib.request,http.cookiejar,pathlib
p=argparse.ArgumentParser();p.add_argument('--fail-stage',choices=['ASSET_CHECK','GENERATE','FINALIZE']);p.add_argument('--delay',type=int,default=1500);p.add_argument('--persona',choices=['A','B']);a=p.parse_args()
opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def call(path,data=None,headers=None):
    if isinstance(data,dict):data=json.dumps(data).encode();headers={'Content-Type':'application/json'}
    r=opener.open(urllib.request.Request('http://frontend:3000/api'+path,data=data,headers=headers or {}));return json.load(r)
call('/session',{})
if a.persona:call('/dev/session/switch',{'persona':a.persona})
boundary='DemoUpload';image=pathlib.Path('/demo/demo.png').read_bytes()
body=b'--DemoUpload\r\nContent-Disposition: form-data; name="templateId"\r\n\r\nportrait\r\n--DemoUpload\r\nContent-Disposition: form-data; name="file"; filename="demo.png"\r\nContent-Type: image/png\r\n\r\n'+image+b'\r\n--DemoUpload--\r\n'
asset=call('/assets',body,{'Content-Type':'multipart/form-data; boundary='+boundary})
t=call('/tasks',{'templateId':'portrait','assetIds':[asset['assetId']],'simulation':{'failStage':a.fail_stage,'delaysMs':dict.fromkeys(['assetCheck','generate','finalize'],a.delay)}})
print('Accepted',json.dumps(t,ensure_ascii=False))
while t['status'] in ['QUEUED','RUNNING']:
    time.sleep(1);t=call('/tasks/'+t['id']);print(t['status'],t['currentStage'])
print(json.dumps(t,ensure_ascii=False,indent=2))
