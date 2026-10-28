import urllib.request,http.cookiejar,json,pathlib,sys
client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def get(path,data=None):
    req=urllib.request.Request('http://frontend:3000/api'+path,data=json.dumps(data).encode() if data is not None else None,headers={'Content-Type':'application/json'})
    response=client.open(req,timeout=15);body=response.read()
    return json.loads(body) if 'json' in response.headers.get('Content-Type','') else body
get('/session',{});get('/dev/session/switch',{'persona':'A'})
tasks=get('/me/tasks?limit=100');assert len(tasks)>=2
first=get('/me/tasks?limit=1');second=get('/me/tasks?limit=1&offset=1');assert len(first)==len(second)==1 and first[0]['id']!=second[0]['id']
assert len(get('/categories'))==3
file=pathlib.Path('/evidence/persistence-baseline.json')
snapshot={t['id']:{'status':t['status'],'version':t['stateVersion']} for t in tasks}
if sys.argv[-1]=='save':file.write_text(json.dumps(snapshot),encoding='utf8')
else:
    assert snapshot==json.loads(file.read_text()),'terminal records changed after restart'
    for t in tasks:
        if t['status']=='SUCCEEDED':assert get(t['resultUrl'].removeprefix('/api')).startswith(b'\x89PNG')
    pathlib.Path('/evidence/persistence-result.json').write_text(json.dumps({'result':'PASS','checks':['history pagination','categories','terminal history survives backend restart','result file survives backend restart'],'tasks':len(tasks)}),encoding='utf8')
print('PASS',sys.argv[-1],len(tasks))
