import urllib.request, urllib.error, http.cookiejar, json, time, pathlib
base='http://frontend:3000/api'
def client():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
a,b=client(),client()
def req(c,path,data=None,headers=None):
    if isinstance(data,dict): data=json.dumps(data).encode(); headers={'Content-Type':'application/json'}
    response=c.open(urllib.request.Request(base+path,data=data,headers=headers or {}),timeout=15)
    body=response.read()
    return json.loads(body) if 'json' in response.headers.get('Content-Type','') else body
for i in range(90):
    try: req(a,'/health'); break
    except Exception: time.sleep(2)
else: raise RuntimeError('services did not become ready')
req(a,'/session',{});req(b,'/session',{})
from concurrent.futures import ThreadPoolExecutor
with ThreadPoolExecutor(max_workers=2) as pool:
    def switch_and_read(_):
        visitor=client()
        req(visitor,'/dev/session/switch',{'persona':'A'})
        return req(visitor,'/me')
    identities=list(pool.map(switch_and_read,range(2)))
assert all(identity['id']=='演示访客A' for identity in identities)
assert req(a,'/me')['demoEnabled'] is True
templates=req(a,'/templates');assert len(templates)==12
assert set(t['requiredImageCount'] for t in templates)=={1,2,3}
assert len(set(t['category'] for t in templates))==3
for template in templates:
    assert req(a,template['coverUrl'].removeprefix('/api'))[:8]==b'\x89PNG\r\n\x1a\n'
assert next(t for t in templates if t['id']=='portrait')['title']=='午后肖像', 'seed encoding must be UTF-8'
assert all('required_image_count' not in t and 'requiredImageCount' in t for t in templates)
image=req(a,'/public/cover/portrait');assert image[:4]==b'\x89PNG'
def upload(c,body=image):
    boundary='StudioBoundary'
    data=(f'--{boundary}\r\nContent-Disposition: form-data; name="templateId"\r\n\r\nportrait\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="demo.png"\r\nContent-Type: image/png\r\n\r\n').encode()+body+f'\r\n--{boundary}--\r\n'.encode()
    return req(c,'/assets',data,{'Content-Type':'multipart/form-data; boundary='+boundary})
asset=upload(a)
def expect_error(action,code,message=None):
    try: action()
    except urllib.error.HTTPError as e:
        assert e.code==code,(e.code,code)
        if message: assert message in json.load(e).get('message','')
        return
    raise AssertionError('expected rejection')
expect_error(lambda:upload(a,b'fake image'),415)
expect_error(lambda:upload(a,b'\x89PNG\r\n\x1a\n\x00\x00'),415,'图片')
expect_error(lambda:upload(a,b'\xff\xd8\xff\xe0\x00\x10JFIF\x00'),415,'图片')
expect_error(lambda:req(b,'/assets/'+asset['assetId']+'/content'),404)
expect_error(lambda:req(a,'/templates/missing-template'),404)
expect_error(lambda:req(a,'/tasks',{'templateId':'portrait','assetIds':[]}),400)
expect_error(lambda:req(b,'/tasks',{'templateId':'portrait','assetIds':[asset['assetId']]}),400)
expect_error(lambda:upload(a,b'x'*(5242880+1)),413)
expect_error(lambda:req(client(),'/me/tasks'),401)
for path in ['/me/tasks?limit=0','/me/tasks?limit=101','/me/tasks?offset=-1']:
    expect_error(lambda:req(a,path),400)
expect_error(lambda:req(a,'/tasks',{'templateId':'duet','assetIds':[asset['assetId'],asset['assetId']]}),400)
def create(fail=None,delay=50):
    return req(a,'/tasks',{'templateId':'portrait','assetIds':[asset['assetId']],'simulation':{'failStage':fail,'delaysMs':{'assetCheck':delay,'generate':delay,'finalize':delay}}})
def terminal(task,timeout=20):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        state=req(a,'/tasks/'+task['id'])
        if state['status'] in ['SUCCEEDED','FAILED']:return state
        time.sleep(.1)
    raise AssertionError('task did not finish: '+task['id'])
expect_error(lambda:req(a,'/tasks',{'templateId':'portrait','assetIds':[asset['assetId']],'simulation':{'delaysMs':'invalid'}}),400)
expect_error(lambda:req(a,'/tasks',{'templateId':'portrait','assetIds':[asset['assetId']],'simulation':{'failStage':'INVALID'}}),400)
for bad in [-1,60001,1.5,'1000']:
    expect_error(lambda:req(a,'/tasks',{'templateId':'portrait','assetIds':[asset['assetId']],'simulation':{'delaysMs':{'generate':bad}}}),400)
expect_error(lambda:req(a,'/dev/session/switch',{}),400)
start=time.monotonic();slow=create(delay=1200);assert time.monotonic()-start<2
assert any(t['id']==slow['id'] for t in req(a,'/me/tasks/active'))
expect_error(lambda:req(a,'/tasks/'+slow['id']+'/result'),404)
expect_error(lambda:req(b,'/tasks/'+slow['id']),404)
# Do not query while the Java background pipeline executes.
time.sleep(5)
done=req(a,'/tasks/'+slow['id']);assert done['status']=='SUCCEEDED',done
assert not any(t['id']==slow['id'] for t in req(a,'/me/tasks/active'))
assert [s['status'] for s in done['steps']]==['SUCCEEDED']*3
assert [s['stage'] for s in done['steps']]==['ASSET_CHECK','GENERATE','FINALIZE']
assert [s['sequenceNo'] for s in done['steps']]==[0,1,2]
assert all('startedAt' in s and 'finishedAt' in s and 'resultUrl' not in s for s in done['steps'])
result=req(a,done['resultUrl'].removeprefix('/api'));assert result[:4]==b'\x89PNG'
version=done['stateVersion'];assert req(a,'/tasks/'+slow['id'])['stateVersion']==version
expect_error(lambda:req(b,'/tasks/'+slow['id']),404)
expect_error(lambda:req(b,done['resultUrl'].removeprefix('/api')),404)
records=[done]
for repeat in range(2):
    for index,stage in enumerate(['ASSET_CHECK','GENERATE','FINALIZE']):
        t=terminal(create(stage));assert t['status']=='FAILED' and t['failureStage']==stage,t
        assert [s['status'] for s in t['steps']]==['SUCCEEDED']*index+['FAILED']+['PENDING']*(2-index),t
        records.append(t)
multi=[create(delay=250),create('GENERATE',250),create(delay=250)]
states=[terminal(t)['status'] for t in multi];assert states==['SUCCEEDED','FAILED','SUCCEEDED'],states
assert len(req(b,'/me/tasks'))==0
assert len(req(a,'/me/tasks'))>=10
asset2,asset3=upload(a),upload(a)
for template_id,asset_ids in [('duet',[asset['assetId'],asset2['assetId']]),('mountain',[asset['assetId'],asset2['assetId'],asset3['assetId']])]:
    made=req(a,'/tasks',{'templateId':template_id,'assetIds':asset_ids,'simulation':{'delaysMs':{'assetCheck':0,'generate':0,'finalize':0}}})
    assert terminal(made)['status']=='SUCCEEDED'
assert len(req(a,'/me/tasks?limit=3&offset=0'))==3
assert len(req(a,'/me/tasks?limit=3&offset=3'))==3
report={'result':'PASS','time':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'checks':['twelve templates and local PNG covers','three categories and 1/2/3 image requirements','one/two/three asset tasks','real PNG upload','invalid and damaged images rejected','oversized file rejected','asset ownership and duplicate assets','unauthenticated and cross-user access denied','invalid pagination and simulation parameters','active and pending-result boundaries','async acceptance','completion without queries','three persisted steps','terminal stability','success result PNG','task and result ownership','three deterministic stage failures stop later steps','three independent queued tasks','personal history and pagination'],'tasks':records}
pathlib.Path('/evidence').mkdir(exist_ok=True)
pathlib.Path('/evidence/smoke.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k!='tasks'},ensure_ascii=False))
