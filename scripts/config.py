"""Validate the non-demo configuration through the running HTTP stack."""
import urllib.request,urllib.error,http.cookiejar,json,pathlib,time
client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def request(path,data=None,headers=None):
    if isinstance(data,dict):data=json.dumps(data).encode();headers={'Content-Type':'application/json'}
    response=client.open(urllib.request.Request('http://frontend:3000/api'+path,data=data,headers=headers or {}),timeout=15)
    body=response.read()
    return json.loads(body) if 'json' in response.headers.get('Content-Type','') else body
def error(path,data,status):
    try:request(path,data)
    except urllib.error.HTTPError as e:assert e.code==status;return
    raise AssertionError('request should have been rejected')
assert request('/session',{})['demoEnabled'] is False
assert request('/me')['demoEnabled'] is False
error('/dev/session/switch',{'persona':'A'},404)
data=b'--Config\r\nContent-Disposition: form-data; name="templateId"\r\n\r\nportrait\r\n--Config\r\nContent-Disposition: form-data; name="file"; filename="demo.png"\r\nContent-Type: image/png\r\n\r\n'+pathlib.Path('/demo/demo.png').read_bytes()+b'\r\n--Config--\r\n'
asset=request('/assets',data,{'Content-Type':'multipart/form-data; boundary=Config'})
body={'templateId':'portrait','assetIds':[asset['assetId']]}
error('/tasks',{**body,'simulation':{'failStage':'GENERATE'}},400)
assert request('/me/tasks')==[]
task=request('/tasks',body);deadline=time.monotonic()+30
while task['status'] in ['QUEUED','RUNNING']:
    assert time.monotonic()<deadline
    time.sleep(.5);task=request('/tasks/'+task['id'])
assert task['status']=='SUCCEEDED',task
assert request(task['resultUrl'].removeprefix('/api')).startswith(b'\x89PNG')
print(json.dumps({'result':'PASS','checks':['non-demo capability flag','switch endpoint disabled','simulation parameters rejected','default task generation still works','private result readable']},ensure_ascii=False))
